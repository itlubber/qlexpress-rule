import {
  clearCurrentUser,
  filterMenus,
  getCurrentUser,
  hasPermission,
  setCurrentUser
} from '@/security/permissionState'

describe('控制台最终权限状态', () => {
  afterEach(() => clearCurrentUser())

  test('仅保留当前用户拥有访问权限的菜单', () => {
    setCurrentUser({
      userId: 9,
      username: 'reviewer',
      permissions: ['approval:view', 'account:view']
    })
    const menus = [
      { index: '/approval', permission: 'approval:view' },
      { index: '/account', permission: 'account:view' },
      { index: '/rule', permission: 'rule:view' }
    ]

    expect(filterMenus(menus).map(item => item.index))
      .toEqual(['/approval', '/account'])
    expect(hasPermission('approval:view')).toBe(true)
    expect(hasPermission('approval:approve')).toBe(false)
  })

  test('未加载账户时保持兼容，不隐藏原有菜单', () => {
    const menus = [
      { index: '/project', permission: 'project:view' },
      { index: '/rule', permission: 'rule:view' }
    ]

    expect(filterMenus(menus)).toEqual(menus)
  })

  test('状态保存不可变副本并可清空', () => {
    const source = { username: 'admin', permissions: ['account:manage'] }
    setCurrentUser(source)
    source.permissions.push('role:manage')

    expect(getCurrentUser().permissions).toEqual(['account:manage'])
    clearCurrentUser()
    expect(getCurrentUser()).toBeNull()
  })
})
