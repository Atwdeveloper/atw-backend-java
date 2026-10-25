'use strict';

// Custom GitHub Action.
// Azure authentication comes from azure/login earlier in the workflow.

const { execFileSync } = require('node:child_process');
const { createHash } = require('node:crypto');

function input(name) {
  const value = process.env['INPUT_' + name.toUpperCase()];

  if (!value || !value.trim()) {
    throw new Error('Missing action input: ' + name);
  }

  return value.trim();
}

function azure(args, json = false) {
  const count =
    args[0] === 'containerapp' && args[1] === 'update' ? 2 : 3;
  const operation = args.slice(0, count).join(' ');

  console.log('Azure operation: ' + operation);

  try {
    const output = execFileSync(
      'az',
      [
        ...args,
        '--only-show-errors',
        '--output',
        json ? 'json' : 'none'
      ],
      {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        maxBuffer: 32 * 1024 * 1024
      }
    );

    return json ? JSON.parse(output) : undefined;
  } catch (error) {
    console.error('Azure operation failed: ' + operation);

    // Display Azure's error without dumping arguments or configuration JSON.
    if (error.stderr) {
      console.error(String(error.stderr));
    }

    throw error;
  }
}

function environmentName(key) {
  const validName = /^backend-[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;

  if (typeof key !== 'string' || !validName.test(key)) {
    throw new Error(
      'Names must start with backend- and use lowercase words separated by single hyphens.'
    );
  }

  return key
    .slice('backend-'.length)
    .replaceAll('-', '_')
    .toUpperCase();
}

function deploy() {
  const app = input('app_name');
  const group = input('resource_group');
  const image = input('image');
  const vault = input('key_vault_name');
  const identity = input('runtime_identity_resource_id');
  const endpoint = input('app_config_endpoint');

  // Read ordinary backend settings from App Configuration.
  const configuration = azure(
    [
      'appconfig', 'kv', 'list',
      '--endpoint', endpoint,
      '--auth-mode', 'login',
      '--key', 'backend-*',
      '--label', 'dev',
      '--all'
    ],
    true
  );

  // List enabled secret names only.
  // Container Apps retrieves their values using the runtime identity.
  const secretNames = azure(
    [
      'keyvault', 'secret', 'list',
      '--vault-name', vault,
      '--query',
      "[?starts_with(name, 'backend-') && attributes.enabled].name"
    ],
    true
  );

  if (
    !Array.isArray(configuration) ||
    !configuration.length ||
    !Array.isArray(secretNames) ||
    !secretNames.length
  ) {
    throw new Error(
      'Expected backend- settings with label dev in App Configuration and enabled backend- secrets in Key Vault.'
    );
  }

  const settings = new Map();

  for (const item of configuration) {
    const name = environmentName(item.key);
    const type = item.content_type || item.contentType || '';

    if (type.includes('keyvaultref') || type.includes('ff+')) {
      throw new Error(
        'Use ordinary App Configuration key-values; keep backend secrets directly in Key Vault.'
      );
    }

    if (
      typeof item.value !== 'string' ||
      item.value.includes('\0') ||
      item.value.startsWith('secretref:')
    ) {
      throw new Error(
        'App Configuration values must be ordinary text values.'
      );
    }

    if (settings.has(name)) {
      throw new Error('Duplicate backend setting found.');
    }

    settings.set(name, item.value);
  }

  const secretReferences = [];

  for (const key of secretNames) {
    const name = environmentName(key);

    if (settings.has(name)) {
      throw new Error(
        'A backend setting exists in both stores. Keep each setting in exactly one store.'
      );
    }

    const shortName =
      'kv-' +
      createHash('sha256')
        .update(key)
        .digest('hex')
        .slice(0, 16);

    const uri =
      'https://' + vault + '.vault.azure.net/secrets/' + key;

    secretReferences.push(
      shortName +
      '=keyvaultref:' +
      uri +
      ',identityref:' +
      identity
    );

    settings.set(name, 'secretref:' + shortName);
  }

  // NEW: Configure image-pull authentication using the attached runtime UAMI.
  // Example: registry.azurecr.io/repository:tag -> registry.azurecr.io
  const registryServer = image.split('/')[0];

  azure([
    'containerapp', 'registry', 'set',
    '--name', app,
    '--resource-group', group,
    '--server', registryServer,
    '--identity', identity
  ]);

  // Create/update Key Vault references after validating all settings.
  azure([
    'containerapp', 'secret', 'set',
    '--name', app,
    '--resource-group', group,
    '--secrets', ...secretReferences
  ]);

  // Update the image and environment-variable mappings together.
  azure([
    'containerapp', 'update',
    '--name', app,
    '--resource-group', group,
    '--image', image,
    '--set-env-vars',
    ...Array.from(settings, ([name, value]) => name + '=' + value)
  ]);

  console.log(
    'Deployed with ' +
    configuration.length +
    ' ordinary settings and ' +
    secretNames.length +
    ' Key Vault references.'
  );
}

function main() {
  switch (input('operation')) {
    case 'build-jar':
      execFileSync(
        'bash',
        ['./mvnw', '--batch-mode', '-DskipTests', 'package'],
        { stdio: 'inherit' }
      );
      return;

    case 'acr-login':
      azure(['acr', 'login', '--name', input('acr_name')]);
      console.log('ACR login completed.');
      return;

    case 'deploy':
      deploy();
      return;

    default:
      throw new Error(
        'Unsupported operation. Choose build-jar, acr-login, or deploy.'
      );
  }
}

try {
  main();
} catch (error) {
  const message =
    error && error.status !== undefined
      ? 'A build or Azure command failed (exit ' +
        error.status +
        '). Check the Azure error above for the exact cause.'
      : error instanceof SyntaxError || error instanceof TypeError
        ? 'The configuration response had an unexpected format.'
        : error.message || 'Backend task failed.';

  console.error(
    '::error::' +
    String(message)
      .replaceAll('%', '%25')
      .replaceAll('\r', '%0D')
      .replaceAll('\n', '%0A')
  );

  process.exitCode = 1;
}
