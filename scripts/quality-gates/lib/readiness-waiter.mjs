export function parseReadinessArgs(args) {
  const values = new Map()
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index]
    const value = args[index + 1]
    if (!key?.startsWith('--') || value === undefined || value.startsWith('--')) {
      throw new Error(`invalid argument near ${key || '<empty>'}`)
    }
    values.set(key, value)
  }
  const baseUrl = values.get('--base-url')
  if (!baseUrl) throw new Error('--base-url is required')
  const parsed = new URL(baseUrl)
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error('--base-url must use http or https')
  }
  const timeoutSeconds = Number(values.get('--timeout-seconds'))
  if (!Number.isFinite(timeoutSeconds) || timeoutSeconds <= 0) {
    throw new Error('--timeout-seconds must be greater than zero')
  }
  const intervalMs = values.has('--interval-ms')
    ? Number(values.get('--interval-ms'))
    : 500
  if (!Number.isFinite(intervalMs) || intervalMs < 10) {
    throw new Error('--interval-ms must be at least 10')
  }
  return {
    baseUrl: baseUrl.replace(/\/$/, ''),
    timeoutMs: timeoutSeconds * 1000,
    intervalMs,
  }
}

export async function waitForReadiness(config) {
  const baseUrl = config.baseUrl.replace(/\/$/, '')
  const deadline = Date.now() + config.timeoutMs
  let lastReadiness = 'UNKNOWN'
  while (Date.now() <= deadline) {
    const liveness = await readStatus(`${baseUrl}/actuator/health/liveness`)
    if (liveness === 'UNAVAILABLE') {
      lastReadiness = 'WAITING_FOR_LIVENESS'
      await delay(config.intervalMs)
      continue
    }
    if (liveness !== 'UP') {
      throw new Error(`liveness is ${liveness}`)
    }
    lastReadiness = await readStatus(`${baseUrl}/actuator/health/readiness`)
    if (lastReadiness === 'UP') {
      return { status: 'UP' }
    }
    await delay(config.intervalMs)
  }
  throw new Error(`readiness timed out with status ${lastReadiness}`)
}

async function readStatus(url) {
  let response
  try {
    response = await fetch(url)
  } catch {
    return 'UNAVAILABLE'
  }
  let body
  try {
    body = await response.json()
  } catch {
    if (!response.ok) {
      throw new Error(`probe returned HTTP ${response.status}`)
    }
    throw new Error('probe returned non-JSON response')
  }
  const status = body?.status || 'UNKNOWN'
  if (!response.ok && status === 'UNKNOWN') {
    throw new Error(`probe returned HTTP ${response.status}`)
  }
  return status
}

function delay(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}
