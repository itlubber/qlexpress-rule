#!/usr/bin/env node

import {
  loadRequestDefinition,
  parseCapacityConfig,
  sanitizeTarget,
} from './lib/capacity-config.mjs'
import {
  buildCapacityReport,
  runCapacityScenario,
  writeCapacityReports,
} from './lib/capacity-runner.mjs'

try {
  const config = parseCapacityConfig(process.argv.slice(2))
  const request = await loadRequestDefinition(config.requestFile)
  const { summary, evaluation } = await runCapacityScenario(config, request)
  const report = buildCapacityReport(config, request, summary, evaluation)
  await writeCapacityReports(report, config.reportDir)
  console.log(JSON.stringify({
    result: evaluation.passed ? 'PASS' : 'FAIL',
    target: sanitizeTarget(config.url),
    summary,
    failures: evaluation.failures,
    reportDir: config.reportDir,
  }, null, 2))
  if (!evaluation.passed) process.exitCode = 1
} catch (error) {
  console.error(`[capacity-gate] ${error.message}`)
  process.exitCode = 2
}
