import {
  createDashboardPalette,
  distributionBarOption,
  distributionPieOption,
  geoHeatmapOption,
  ruleSetHitOption
} from '@/charts/dashboardCharts'

describe('dashboard charts', () => {
  test('调色板读取当前主题 CSS 变量', () => {
    document.documentElement.style.setProperty('--el-color-primary', '#123456')
    document.documentElement.style.setProperty('--tianshu-color-secondary', '#654321')
    const palette = createDashboardPalette(document.documentElement)
    expect(palette.primary).toBe('#123456')
    expect(palette.secondary).toBe('#654321')
  })

  test('分布图保留标签、数量和主题色', () => {
    const items = [{ key: '12', label: '12期', count: 8 }]
    expect(distributionBarOption(items).series[0].data).toEqual([8])
    expect(distributionPieOption(items).series[0].data)
      .toEqual([{ name: '12期', value: 8 }])
  })

  test('地图热力图只接收服务端合法坐标点', () => {
    const option = geoHeatmapOption([
      { longitude: 120.12, latitude: 30.28, count: 4 }
    ], { center: [112, 31], zoom: 2.4 })
    expect(option.series[0].coordinateSystem).toBe('geo')
    expect(option.series[0].data).toEqual([[120.12, 30.28, 4]])
    expect(option.geo.center).toEqual([112, 31])
    expect(option.geo.zoom).toBe(2.4)
  })

  test('规则集 TOP 悬浮信息包含进件、命中和命中率', () => {
    const option = ruleSetHitOption([{
      ruleCode: 'R1', ruleName: '规则一', applicationCount: 20,
      hitCount: 5, hitRate: 0.25
    }])

    expect(option.series[0].data).toEqual([5])
    expect(option.tooltip.renderMode).toBe('richText')
    expect(option.tooltip.formatter([{ dataIndex: 0 }]))
      .toBe('规则一\n进件数：20\n命中数：5\n命中率：25.00%')
  })
})
