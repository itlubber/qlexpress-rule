import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'

import {
  parseReadinessArgs,
  waitForReadiness,
} from '../lib/readiness-waiter.mjs'

test('requires an explicit http base URL and positive timeout', () => {
  assert.throws(() => parseReadinessArgs([]), /--base-url/)
  assert.throws(() => parseReadinessArgs([
    '--base-url', 'file:///tmp/server',
    '--timeout-seconds', '10',
  ]), /http/)
  assert.throws(() => parseReadinessArgs([
    '--base-url', 'http://127.0.0.1:8080',
    '--timeout-seconds', '0',
  ]), /timeout/)
})

test('retries readiness until it becomes UP while liveness stays UP', async t => {
  let readinessCalls = 0
  const server = createServer((request, response) => {
    response.setHeader('content-type', 'application/json')
    if (request.url.endsWith('/liveness')) {
      response.end('{"status":"UP"}')
      return
    }
    readinessCalls++
    response.end(JSON.stringify({
      status: readinessCalls >= 3 ? 'UP' : 'OUT_OF_SERVICE',
    }))
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const address = server.address()

  const result = await waitForReadiness({
    baseUrl: `http://127.0.0.1:${address.port}`,
    timeoutMs: 1000,
    intervalMs: 10,
  })

  assert.equal(result.status, 'UP')
  assert.equal(readinessCalls, 3)
})

test('retries a 503 readiness response that reports OUT_OF_SERVICE', async t => {
  let readinessCalls = 0
  const server = createServer((request, response) => {
    response.setHeader('content-type', 'application/json')
    if (request.url.endsWith('/liveness')) {
      response.end('{"status":"UP"}')
      return
    }
    readinessCalls++
    const ready = readinessCalls >= 2
    response.statusCode = ready ? 200 : 503
    response.end(JSON.stringify({
      status: ready ? 'UP' : 'OUT_OF_SERVICE',
    }))
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const address = server.address()

  const result = await waitForReadiness({
    baseUrl: `http://127.0.0.1:${address.port}`,
    timeoutMs: 1000,
    intervalMs: 10,
  })

  assert.equal(result.status, 'UP')
  assert.equal(readinessCalls, 2)
})

test('retries while the liveness endpoint is temporarily unreachable', async t => {
  let livenessCalls = 0
  const server = createServer((request, response) => {
    if (request.url.endsWith('/liveness')) {
      livenessCalls++
      if (livenessCalls === 1) {
        request.socket.destroy()
        return
      }
    }
    response.setHeader('content-type', 'application/json')
    response.end('{"status":"UP"}')
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const address = server.address()

  const result = await waitForReadiness({
    baseUrl: `http://127.0.0.1:${address.port}`,
    timeoutMs: 1000,
    intervalMs: 10,
  })

  assert.equal(result.status, 'UP')
  assert.equal(livenessCalls, 2)
})

test('fails immediately when liveness is not UP', async t => {
  let readinessCalls = 0
  const server = createServer((request, response) => {
    response.setHeader('content-type', 'application/json')
    if (request.url.endsWith('/liveness')) {
      response.end('{"status":"DOWN"}')
      return
    }
    readinessCalls++
    response.end('{"status":"UP"}')
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => server.close(resolve)))
  const address = server.address()

  await assert.rejects(() => waitForReadiness({
    baseUrl: `http://127.0.0.1:${address.port}`,
    timeoutMs: 1000,
    intervalMs: 10,
  }), /liveness.*DOWN/i)
  assert.equal(readinessCalls, 0)
})
