# 变量来源分层并行基准

## 结论

2026-08-28 在正确性测试全部通过后，连续三轮基准中，4 路并行相对顺序解析的中位耗时改善均超过 20% 门槛，因此默认值设为 `4`。部署仍可通过 `SOURCE_RESOLUTION_PARALLELISM` 显式覆盖；设置为 `1` 时保留顺序执行路径。

## 环境

- JDK：OpenJDK 17.0.19（Microsoft build）
- CPU：Intel Core i9-13900KF，24 核、32 逻辑处理器
- 样本：8 个同一依赖层的独立阻塞 API 来源
- 单来源模拟等待：30ms
- 每种模式预热：3 次
- 每种模式测量：9 次
- 正确性：每次测量均断言 8 个来源的完整输出

## 结果

| 轮次 | 顺序中位数 | 4 路中位数 | 中位数改善 | 顺序 p95 | 4 路 p95 |
|---|---:|---:|---:|---:|---:|
| 1 | 249ms | 76ms | 69.2% | 263ms | 91ms |
| 2 | 251ms | 64ms | 74.5% | 264ms | 77ms |
| 3 | 250ms | 63ms | 74.5% | 277ms | 93ms |

## 可重复命令

```powershell
1..3 | ForEach-Object {
  mvn -q -pl rule-engine-server -am `
    "-Dtest=VariableSourceResolverBenchmarkTest" `
    "-Dsurefire.failIfNoSpecifiedTests=false" `
    "-Dtianshu.source-resolution.benchmark=true" test
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
```

此基准验证的是依赖分层、调度开销和阻塞来源的并发收益，不替代真实部署环境的容量测试。真实 API、数据库和名单服务的连接池、限流及下游容量仍需按部署规格单独验收。
