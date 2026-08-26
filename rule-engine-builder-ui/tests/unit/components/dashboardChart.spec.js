const chart = {
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn()
}

vi.mock('@/charts/dashboardCharts', () => ({
  createChartInstance: vi.fn(() => chart)
}))

import { mount } from '@test-utils'
import DashboardChart from '@/components/dashboard/DashboardChart.vue'

describe('DashboardChart', () => {
  beforeEach(() => vi.clearAllMocks())

  test('渲染、主题刷新并在卸载时销毁实例', async () => {
    const wrapper = mount(DashboardChart, {
      props: {
        ariaLabel: '期数占比',
        option: { series: [] }
      }
    })
    expect(chart.setOption).toHaveBeenCalledWith({ series: [] }, true)

    window.dispatchEvent(new Event('tianshu-theme-change'))
    expect(chart.setOption).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    expect(chart.dispose).toHaveBeenCalledOnce()
  })

  test('空数据展示统一空状态且不初始化画布', () => {
    const wrapper = mount(DashboardChart, {
      props: { ariaLabel: '空图表', empty: true, option: { series: [] } }
    })
    expect(wrapper.find('el-empty-stub').attributes('description'))
      .toBe('暂无统计数据')
    expect(chart.setOption).not.toHaveBeenCalled()
  })
})
