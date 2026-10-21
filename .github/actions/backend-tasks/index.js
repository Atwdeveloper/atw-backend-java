'use strict';

// Project-owned JavaScript action. Azure CLI commands execute directly, without
// shell interpolation. Azure login is provided by azure/login earlier in the job.
const { execFileSync } = require('node:child_process');
const { createHash } = require('node:crypto');

function input(name) {
  const value = process.env['INPUT_' + name.toUpperCase()];
  if (!value || !value.trim()) throw new Error('Missing action input: ' + name);
  return value.trim();
}

function azure(args, json = false) {
  const output = execFileSync('az', [
    ...args, '--only-show-errors', '--output', json ? 'json' : 'none'
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 32 * 1024 * 1024 });
  return json ? JSON.parse(output) : undefined;
}

function environmentName(key) {
  if (typeof key !== 'string' || !/^backend-[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(key)) {
    throw new Error('Names must start with backend- and use lowercase words separated by single hyphens.');
  }
  return key.slice('backend-'.length).replaceAll('-', '_').toUpperCase();
}

function deploy() {
  const app = input('app_name');
  const group = input('resource_group');
  const image = input('image');
  const vault = input('key_vault_name');
  const identity = input('runtime_identity_resource_id');
  const endpoint = input('app_config_endpoint');

  const configuration = azure([
    'appconfig', 'kv', 'list', '--endpoint', endpoint, '--auth-mode', 'login',
    '--key', 'backend-*', '--label', 'dev', '--all'
  ], true);
  const secretNames = azure([
    'keyvault', 'secret', 'list', '--vault-name', vault,
    '--query', "[?starts_with(name, 'backend-') && attributes.enabled].name"
  ], true);

  if (!Array.isArray(configuration) || !configuration.length ||
      !Array.isArray(secretNames) || !secretNames.length) {
    throw new Error('Expected backend- settings with label dev in App Configuration and enabled backend- secrets in Key Vault.');
  }

  const settings = new Map();
  for (const item of configuration) {
    const name = environmentName(item.key);
    const type = item.content_type || item.contentType || '';
    if (type.includes('keyvaultref') || type.includes('ff+')) {
      throw new Error('Use ordinary App Configuration key-values; keep backend secrets directly in Key Vault.');
    }
    if (typeof item.value !== 'string' || item.value.includes('\0') ||
        item.value.startsWith('secretref:')) {
      throw new Error('App Configuration values must be ordinary text values.');
    }
    if (settings.has(name)) throw new Error('Duplicate backend setting found.');
    settings.set(name, item.value);
  }

  const secretReferences = [];
  for (const key of secretNames) {
    const name = environmentName(key);
    if (settings.has(name)) {
      throw new Error('A backend setting exists in both stores. Keep each setting in exactly one store.');
    }
    const shortName = 'kv-' + createHash('sha256').update(key).digest('hex').slice(0, 16);
    const uri = 'https://' + vault + '.vault.azure.net/secrets/' + key;
    secretReferences.push(shortName + '=keyvaultref:' + uri + ',identityref:' + identity);
    settings.set(name, 'secretref:' + shortName);
  }

  // Configure references only after every discovered setting has been validated.
  azure([
    'containerapp', 'secret', 'set', '--name', app, '--resource-group', group,
    '--secrets', ...secretReferences
  ]);
  // Update the image and environment together. Existing unrelated variables stay.
  azure([
    'containerapp', 'update', '--name', app, '--resource-group', group,
    '--image', image, '--set-env-vars',
    ...Array.from(settings, ([name, value]) => name + '=' + value)
  ]);
  console.log('Deployed with ' + configuration.length + ' ordinary settings and ' +
              secretNames.length + ' Key Vault references.');
}

function main() {
  switch (input('operation')) {
    case 'build-jar':
      // Invoke the repository's existing Maven wrapper, just as a Maven task does.
      execFileSync('bash', ['./mvnw', '--batch-mode', '-DskipTests', 'package'],
                   { stdio: 'inherit' });
      return;
    case 'acr-login':
      azure(['acr', 'login', '--name', input('acr_name')]);
      console.log('ACR login completed.');
      return;
    case 'deploy':
      deploy();
      return;
    default:
      throw new Error('Unsupported operation. Choose build-jar, acr-login, or deploy.');
  }
}

try {
  main();
} catch (error) {
  // Avoid dumping subprocess arguments or App Configuration values into logs.
  const message = error && error.status !== undefined
    ? 'A build or Azure command failed (exit ' + error.status + '). Check permissions, resource names, networking, and Container App events.'
    : error instanceof SyntaxError || error instanceof TypeError
      ? 'The configuration response had an unexpected format.'
      : error.message || 'Backend task failed.';
  console.error('::error::' + String(message).replaceAll('%', '%25').replaceAll('\r', '%0D').replaceAll('\n', '%0A'));
  process.exitCode = 1;
}
