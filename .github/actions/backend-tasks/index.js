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
    // Do not print command arguments, configuration values,
    // or raw CLI errors, which may contain sensitive values.
    console.error('Azure operation failed: ' + operation);

    throw error;
  }
}

function isBackendKey(key) {
  return (
    /^backend[-_]/i.test(key) ||
    /[-_]backend$/i.test(key)
  );
}

function shouldIncludeKey(key) {
  if (typeof key !== 'string' || !key.length) {
    throw new Error('Configuration names must be non-empty strings.');
  }

  // Explicit backend names take priority.
  // For example, backend-frontend-base-url belongs to the backend.
  if (isBackendKey(key)) {
    return true;
  }

  // Skip explicitly frontend-scoped settings.
  if (
    /^frontend[-_]/i.test(key) ||
    /[-_]frontend$/i.test(key)
  ) {
    return false;
  }

  // Names without an application marker are included.
  return true;
}

function environmentName(key) {
  if (typeof key !== 'string' || !key.length) {
    throw new Error('Configuration names must be non-empty strings.');
  }

  let name = key;

  if (/^backend[-_]/i.test(name)) {
    // Preserve the original behavior for existing backend-prefixed keys.
    // Strip only the prefix, preserving everything after it.
    name = name.replace(/^backend[-_]/i, '');
  } else if (/[-_]backend$/i.test(name)) {
    // Also support a backend suffix when no backend prefix exists.
    name = name.replace(/[-_]backend$/i, '');
  }

  name = name.replaceAll('-', '_').toUpperCase();

  if (!/^[A-Z_][A-Z0-9_]*$/.test(name)) {
    throw new Error(
      'Configuration names must map to valid environment variables: ' +
      'letters, digits, and underscores, without a leading digit.'
    );
  }

  return name;
}

function deploy() {
  const app = input('app_name');
  const group = input('resource_group');
  const image = input('image');
  const vault = input('key_vault_name');
  const identity = input('runtime_identity_resource_id');
  const endpoint = input('app_config_endpoint');

  // Read all ordinary settings labeled dev.
  // No backend-* filter: plain names must also be retrieved.
  const configurationResponse = azure(
    [
      'appconfig', 'kv', 'list',
      '--endpoint', endpoint,
      '--auth-mode', 'login',
      '--label', 'dev',
      '--all'
    ],
    true
  );

  // List enabled secret names only.
  // Container Apps retrieves their values using the runtime identity.
  const secretNamesResponse = azure(
    [
      'keyvault', 'secret', 'list',
      '--vault-name', vault,
      '--query',
      '[?attributes.enabled].name'
    ],
    true
  );

  if (
    !Array.isArray(configurationResponse) ||
    !Array.isArray(secretNamesResponse)
  ) {
    throw new Error(
      'Azure returned an unexpected configuration response.'
    );
  }

  const configuration = configurationResponse.filter(item => {
    if (
      !item ||
      typeof item !== 'object' ||
      typeof item.key !== 'string'
    ) {
      throw new Error(
        'App Configuration returned an invalid configuration entry.'
      );
    }

    return shouldIncludeKey(item.key);
  });

  const secretNames = secretNamesResponse.filter(shouldIncludeKey);

  if (!configuration.length && !secretNames.length) {
    throw new Error(
      'No applicable App Configuration settings labeled dev ' +
      'or enabled Key Vault secrets were found.'
    );
  }

  const settings = new Map();

  function addSetting(name, value) {
    if (settings.has(name)) {
      throw new Error(
        'Multiple configuration keys or secrets map to "' +
        name +
        '". Keep each environment variable in exactly one place ' +
        'and use only one source name for it.'
      );
    }

    settings.set(name, value);
  }

  for (const item of configuration) {
    const name = environmentName(item.key);
    const type = item.content_type || item.contentType || '';

    if (typeof type !== 'string') {
      throw new Error(
        'App Configuration returned an invalid content type.'
      );
    }

    const normalizedType = type.toLowerCase();

    if (
      normalizedType.includes('keyvaultref') ||
      normalizedType.includes('ff+')
    ) {
      throw new Error(
        'Use ordinary App Configuration key-values; ' +
        'keep secrets directly in Key Vault.'
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

    addSetting(name, item.value);
  }

  const secretReferences = [];

  for (const key of secretNames) {
    const name = environmentName(key);

    // Keep the existing secret-reference naming algorithm.
    const shortName =
      'kv-' +
      createHash('sha256')
        .update(key)
        .digest('hex')
        .slice(0, 16);

    const uri =
      'https://' + vault + '.vault.azure.net/secrets/' + key;

    addSetting(name, 'secretref:' + shortName);

    secretReferences.push(
      shortName +
      '=keyvaultref:' +
      uri +
      ',identityref:' +
      identity
    );
  }

  // All configuration validation completes before Azure mutations.

  // Configure image-pull authentication using the attached runtime UAMI.
  // Example: registry.azurecr.io/repository:tag -> registry.azurecr.io
  const registryServer = image.split('/')[0];

  azure([
    'containerapp', 'registry', 'set',
    '--name', app,
    '--resource-group', group,
    '--server', registryServer,
    '--identity', identity
  ]);

  // Create/update Key Vault references when secrets are present.
  if (secretReferences.length > 0) {
    azure([
      'containerapp', 'secret', 'set',
      '--name', app,
      '--resource-group', group,
      '--secrets', ...secretReferences
    ]);
  }

  // Update the image and environment-variable mappings together.
  // Existing unrelated environment variables are not removed.
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
  let message;

  if (error && error.status !== undefined) {
    message =
      'A build or Azure command failed (exit ' +
      error.status +
      '). Check the failed operation above. ' +
      'Azure command details are omitted to avoid exposing configuration values.';
  } else if (
    error instanceof SyntaxError ||
    error instanceof TypeError
  ) {
    message = 'The configuration response had an unexpected format.';
  } else {
    message =
      error && error.message
        ? error.message
        : 'Backend task failed.';
  }

  console.error(
    '::error::' +
    String(message)
      .replaceAll('%', '%25')
      .replaceAll('\r', '%0D')
      .replaceAll('\n', '%0A')
  );

  process.exitCode = 1;
}
