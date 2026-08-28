#!/usr/bin/env node

import {
  parseReadinessArgs,
  waitForReadiness,
} from './lib/readiness-waiter.mjs'

try {
  const config = parseReadinessArgs(process.argv.slice(2))
  const result = await waitForReadiness(config)
  console.log(`[readiness] ${result.status}`)
} catch (error) {
  console.error(`[readiness] ${error.message}`)
  process.exitCode = 1
}
