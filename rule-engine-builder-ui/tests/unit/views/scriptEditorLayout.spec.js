import ScriptEditor from '@/views/designer/ScriptEditor.vue'

describe('ScriptEditor Monaco layout', () => {
  test('折叠变量面板时不触发 Monaco 运行期重建', () => {
    const context = { varPanelCollapsed: false }

    ScriptEditor.methods.toggleVarPanel.call(context)

    expect(context.varPanelCollapsed).toBe(true)
  })

  test('设计器不提供运行期 Monaco 尺寸刷新入口', () => {
    expect(ScriptEditor.methods.scheduleEditorRefresh).toBeUndefined()
  })
})

describe('ScriptEditor variable insertion readiness', () => {
  test('Monaco 就绪前双击变量会在编辑器就绪后完成插入', () => {
    const executeEdits = vi.fn()
    const focus = vi.fn()
    const variable = {
      varCode: 'age',
      _varId: '101',
      _refType: 'VARIABLE',
    }
    const context = {
      monacoEditor: null,
      pendingVarInsertions: [],
      scriptVarRefs: [],
      $nextTick: (callback) => callback(),
      insertVar: ScriptEditor.methods.insertVar,
    }

    ScriptEditor.methods.insertVar.call(context, variable)

    expect(context.pendingVarInsertions).toEqual([variable])

    ScriptEditor.methods.onEditorReady.call(context, {
      getSelection: () => ({ startLineNumber: 1, startColumn: 1 }),
      executeEdits,
      focus,
    })

    expect(executeEdits).toHaveBeenCalledWith('insert-var', [
      expect.objectContaining({ text: 'age' }),
    ])
    expect(context.pendingVarInsertions).toEqual([])
    expect(context.scriptVarRefs).toEqual([
      { refCode: 'age', varId: '101', refType: 'VARIABLE' },
    ])
    expect(focus).toHaveBeenCalled()
  })
})
