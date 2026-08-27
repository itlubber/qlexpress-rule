import { getEndNodeAppearance, normalizeEndScope } from '@/utils/endNodeScope'
import { hasUsableConditionLeaf } from '@/utils/decisionConditionTree'
import { graphContainsDirectedCycle } from '@/utils/flowGraphCycle'

export const FLOW_THEME_COLOR = 'var(--el-color-primary)'
export const ANCHOR_CLICK_TOLERANCE = 10

const COMMON_MENU_OPTIONS = [
  { type: 'exclusive-gateway', label: '条件判断', icon: 'Sort', color: '#FA8C16' },
  { type: 'script-task', label: '执行动作', icon: 'Document', color: '#2F54EB' },
  { type: 'end-event', label: '结束', icon: 'Remove', color: '#FF4D4F' }
]

export const FLOW_MENU_OPTIONS = {
  flow: [COMMON_MENU_OPTIONS[0], COMMON_MENU_OPTIONS[1], { type: 'join-gateway', label: '聚合', icon: 'CopyDocument', color: '#8C8C8C' }, COMMON_MENU_OPTIONS[2]],
  tree: COMMON_MENU_OPTIONS
}

const DIRECTION_VECTOR = {
  top: { x: 0, y: -1 },
  right: { x: 1, y: 0 },
  bottom: { x: 0, y: 1 },
  left: { x: -1, y: 0 }
}

const ANCHOR_DIRECTION_BY_SUFFIX = {
  0: 'top',
  1: 'right',
  2: 'bottom',
  3: 'left'
}

const DEFAULT_LAYOUT_OPTIONS = {
  horizontalGap: 240,
  verticalGap: 140,
  collisionGap: 36,
  maxCollisionAttempts: 100,
  rootColumns: 3,
}

const TOOLBAR_LAYOUT = {
  originX: 400,
  originY: 300,
  columns: 4,
  horizontalGap: 220,
  verticalGap: 140,
}

function getPointerPosition(payload) {
  const event = payload && payload.e
  if (!event || !Number.isFinite(event.clientX) || !Number.isFinite(event.clientY)) return null
  return { x: event.clientX, y: event.clientY }
}

export function createAnchorGesture(payload) {
  const point = getPointerPosition(payload)
  if (!point) return null
  return {
    startX: point.x,
    startY: point.y,
    maxDistance: 0
  }
}

export function updateAnchorGesture(gesture, payload) {
  const point = getPointerPosition(payload)
  if (!gesture || !point) return gesture
  const distance = Math.hypot(point.x - gesture.startX, point.y - gesture.startY)
  return {
    ...gesture,
    maxDistance: Math.max(gesture.maxDistance, distance)
  }
}

export function isAnchorClickGesture(gesture) {
  return Boolean(gesture && gesture.maxDistance <= ANCHOR_CLICK_TOLERANCE)
}

export function resolveAnchorDirection(anchor, node) {
  const deltaX = anchor.x - node.x
  const deltaY = anchor.y - node.y
  if (Math.abs(deltaX) > Math.abs(deltaY)) return deltaX >= 0 ? 'right' : 'left'
  return deltaY >= 0 ? 'bottom' : 'top'
}

function cloneGraphData(graph) {
  return JSON.parse(JSON.stringify(graph || { nodes: [], edges: [] }))
}

function resolveAnchorIdDirection(anchorId) {
  const match = String(anchorId || '').match(/_(\d+)$/)
  return match ? ANCHOR_DIRECTION_BY_SUFFIX[match[1]] || null : null
}

function resolveStoredAnchorDirection(edge, node, role) {
  if (!edge || !node) return null
  const point = role === 'source' ? edge.startPoint : edge.endPoint
  if (point && Number.isFinite(point.x) && Number.isFinite(point.y) &&
    (Math.abs(point.x - node.x) > 1 || Math.abs(point.y - node.y) > 1)) {
    return resolveAnchorDirection(point, node)
  }
  return resolveAnchorIdDirection(role === 'source' ? edge.sourceAnchorId : edge.targetAnchorId)
}

function fallbackDirection(sourceNode, targetNode) {
  const deltaX = targetNode.x - sourceNode.x
  const deltaY = targetNode.y - sourceNode.y
  if (Math.abs(deltaX) >= Math.abs(deltaY)) return { x: deltaX >= 0 ? 1 : -1, y: 0 }
  return { x: 0, y: deltaY >= 0 ? 1 : -1 }
}

function mergeAxis(sourceValue, targetValue, fallbackValue) {
  if (sourceValue && targetValue && sourceValue !== targetValue) return sourceValue
  return sourceValue || targetValue || fallbackValue || 0
}

export function resolveEdgeDirections(edge, sourceNode, targetNode) {
  const sourceDirection = resolveStoredAnchorDirection(edge, sourceNode, 'source')
  const targetDirection = resolveStoredAnchorDirection(edge, targetNode, 'target')
  const sourceVector = DIRECTION_VECTOR[sourceDirection] || { x: 0, y: 0 }
  const targetVector = DIRECTION_VECTOR[targetDirection] || { x: 0, y: 0 }
  const fallback = fallbackDirection(sourceNode, targetNode)
  const hasAnchorConstraint = Boolean(sourceDirection || targetDirection)
  const x = mergeAxis(sourceVector.x, -targetVector.x, hasAnchorConstraint ? 0 : fallback.x)
  const y = mergeAxis(sourceVector.y, -targetVector.y, hasAnchorConstraint ? 0 : fallback.y)
  return {
    x: x || (y ? 0 : fallback.x),
    y: y || (x ? 0 : fallback.y),
    sourceDirection,
    targetDirection
  }
}

function getLayoutNodeSize(node) {
  const properties = node.properties || {}
  if (node.type === 'dynamic-group' || properties.isGroup) {
    return {
      width: properties.width || node.width || 200,
      height: properties.height || node.height || 120
    }
  }
  if (node.type === 'script-task') return { width: 160, height: 42 }
  if (node.type === 'exclusive-gateway') return { width: 90, height: 70 }
  return { width: 50, height: 50 }
}

function overlapsPlacedNode(candidate, node, placed, gap) {
  const size = getLayoutNodeSize(node)
  return placed.some(item => {
    const placedSize = getLayoutNodeSize(item.node)
    return Math.abs(candidate.x - item.x) < (size.width + placedSize.width) / 2 + gap &&
      Math.abs(candidate.y - item.y) < (size.height + placedSize.height) / 2 + gap
  })
}

function collisionOffset(attempt, direction, options) {
  if (attempt === 0) return { x: 0, y: 0 }
  if (direction.x || direction.y) {
    const step = Math.ceil(attempt / 2)
    const sign = attempt % 2 === 1 ? 1 : -1
    return direction.x
      ? { x: 0, y: sign * step * options.verticalGap }
      : { x: sign * step * options.horizontalGap, y: 0 }
  }
  const step = Math.ceil(attempt / 2)
  const sign = attempt % 2 === 1 ? 1 : -1
  return { x: sign * step * options.horizontalGap, y: step * options.verticalGap }
}

function findNonOverlappingPosition(base, node, placed, direction, options) {
  for (let attempt = 0; attempt <= options.maxCollisionAttempts; attempt++) {
    const offset = collisionOffset(attempt, direction, options)
    const candidate = { x: base.x + offset.x, y: base.y + offset.y }
    if (!overlapsPlacedNode(candidate, node, placed, options.collisionGap)) return candidate
  }
  return base
}

function findNearestIncomingEdge(node, edges, originalPositions) {
  const target = originalPositions.get(node.id)
  const nearest = (edges || []).reduce((closest, edge) => {
    const source = originalPositions.get(edge.sourceNodeId)
    if (!source || !target) return closest
    const distance = Math.pow(source.x - target.x, 2) + Math.pow(source.y - target.y, 2)
    return !closest || distance < closest.distance ? { edge, distance } : closest
  }, null)
  return nearest ? nearest.edge : null
}

function compactGridPosition(index, origin, columns, horizontalGap, verticalGap) {
  return {
    x: origin.x + (index % columns) * horizontalGap,
    y: origin.y + Math.floor(index / columns) * verticalGap,
  }
}

function getStableTopologicalOrder(nodes, edges) {
  const stableOrder = new Map(nodes.map((node, index) => [node.id, index]))
  const indegree = new Map(nodes.map(node => [node.id, 0]))
  const outgoing = new Map(nodes.map(node => [node.id, []]))
  edges.forEach(edge => {
    indegree.set(edge.targetNodeId, indegree.get(edge.targetNodeId) + 1)
    outgoing.get(edge.sourceNodeId).push(edge)
  })
  const queue = nodes.filter(node => indegree.get(node.id) === 0)
    .sort((a, b) => stableOrder.get(a.id) - stableOrder.get(b.id))
  const result = []
  while (queue.length) {
    const node = queue.shift()
    result.push(node)
    outgoing.get(node.id).forEach(edge => {
      const nextIndegree = indegree.get(edge.targetNodeId) - 1
      indegree.set(edge.targetNodeId, nextIndegree)
      if (nextIndegree === 0) {
        queue.push(nodes[stableOrder.get(edge.targetNodeId)])
        queue.sort((a, b) => stableOrder.get(a.id) - stableOrder.get(b.id))
      }
    })
  }
  nodes.forEach(node => {
    if (!result.some(item => item.id === node.id)) result.push(node)
  })
  return result
}

function refreshDynamicGroupBounds(nodes) {
  const byId = new Map(nodes.map(node => [node.id, node]))
  nodes.filter(node => node.type === 'dynamic-group' || (node.properties && node.properties.isGroup)).forEach(group => {
    const members = ((group.properties && group.properties.children) || [])
      .map(id => byId.get(id))
      .filter(Boolean)
    const models = members.map(node => {
      const size = getLayoutNodeSize(node)
      return { x: node.x, y: node.y, width: size.width, height: size.height }
    })
    const bounds = calculateGroupBounds(models)
    if (!bounds) return
    group.x = bounds.x
    group.y = bounds.y
    group.properties = {
      ...(group.properties || {}),
      width: bounds.width,
      height: bounds.height
    }
  })
}

function clearEdgeGeometry(edge) {
  delete edge.startPoint
  delete edge.endPoint
  delete edge.pointsList
  delete edge.points
  if (edge.text && typeof edge.text === 'object') edge.text = edge.text.value || ''
}

export function layoutGraphByAnchors(graph, layoutOptions = {}) {
  const result = cloneGraphData(graph)
  const options = { ...DEFAULT_LAYOUT_OPTIONS, ...layoutOptions }
  const businessNodes = (result.nodes || []).filter(node => node.type !== 'dynamic-group' && !(node.properties && node.properties.isGroup))
  const byId = new Map(businessNodes.map(node => [node.id, node]))
  const originalPositions = new Map(businessNodes.map(node => [node.id, { x: node.x, y: node.y }]))
  const edges = (result.edges || []).filter(edge => byId.has(edge.sourceNodeId) && byId.has(edge.targetNodeId))
  const edgeDirections = new Map(edges.map(edge => [
    edge,
    resolveEdgeDirections(edge, byId.get(edge.sourceNodeId), byId.get(edge.targetNodeId))
  ]))
  const incoming = new Map(businessNodes.map(node => [node.id, []]))
  edges.forEach(edge => incoming.get(edge.targetNodeId).push(edge))
  const positions = new Map()
  const placed = []
  const rootOrigin = businessNodes.reduce((origin, node) => ({
    x: Math.min(origin.x, node.x),
    y: Math.min(origin.y, node.y),
  }), { x: Number.POSITIVE_INFINITY, y: Number.POSITIVE_INFINITY })
  let rootIndex = 0

  getStableTopologicalOrder(businessNodes, edges).forEach(node => {
    const primaryEdge = findNearestIncomingEdge(node, incoming.get(node.id), originalPositions)
    const source = primaryEdge ? byId.get(primaryEdge.sourceNodeId) : null
    const sourcePosition = source ? positions.get(source.id) : null
    const direction = primaryEdge ? edgeDirections.get(primaryEdge) : null
    const hasPrimaryPosition = Boolean(sourcePosition && direction)
    const base = hasPrimaryPosition
      ? {
          x: sourcePosition.x + direction.x * options.horizontalGap,
          y: sourcePosition.y + direction.y * options.verticalGap
        }
      : compactGridPosition(
          rootIndex++,
          rootOrigin,
          options.rootColumns,
          options.horizontalGap,
          options.verticalGap
        )
    const layoutDirection = hasPrimaryPosition ? direction : { x: 0, y: 0 }
    const position = findNonOverlappingPosition(base, node, placed, layoutDirection, options)
    node.x = position.x
    node.y = position.y
    positions.set(node.id, position)
    placed.push({ node, ...position })
  })

  refreshDynamicGroupBounds(result.nodes || [])
  ;(result.edges || []).forEach(clearEdgeGeometry)
  return result
}

export function findToolbarNodePosition(nodes, layoutOptions = {}) {
  const options = { ...TOOLBAR_LAYOUT, ...layoutOptions }
  const graphNodes = nodes || []
  for (let index = 0; index < 200; index++) {
    const candidate = compactGridPosition(
      index,
      { x: options.originX, y: options.originY },
      options.columns,
      options.horizontalGap,
      options.verticalGap
    )
    const occupied = graphNodes.some(node =>
      Math.abs(node.x - candidate.x) < 180 &&
      Math.abs(node.y - candidate.y) < 100
    )
    if (!occupied) return candidate
  }
  return compactGridPosition(
    graphNodes.length,
    { x: options.originX, y: options.originY },
    options.columns,
    options.horizontalGap,
    options.verticalGap
  )
}

export function findAvailableNodePosition(nodes, sourceNode, direction, distance = 180) {
  const vector = DIRECTION_VECTOR[direction] || DIRECTION_VECTOR.bottom
  const graphNodes = nodes || []
  for (let step = 1; step <= 50; step++) {
    const position = {
      x: sourceNode.x + vector.x * distance * step,
      y: sourceNode.y + vector.y * distance * step
    }
    const occupied = graphNodes.some(node => node.id !== sourceNode.id && Math.abs(node.x - position.x) < 140 && Math.abs(node.y - position.y) < 100)
    if (!occupied) return position
  }
  return {
    x: sourceNode.x + vector.x * distance * 51,
    y: sourceNode.y + vector.y * distance * 51
  }
}

export function createFlowNodeData(type, { x, y, terminationScope } = {}) {
  const scope = normalizeEndScope(terminationScope)
  const labelMap = {
    'start-event': '开始',
    'end-event': getEndNodeAppearance(scope).name,
    'exclusive-gateway': '条件判断',
    'script-task': '执行动作',
    'join-gateway': '聚合'
  }
  const idSuffix = Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
  return {
    type,
    x,
    y,
    properties: {
      nodeName: labelMap[type] || type,
      nodeCode: type.toUpperCase().replace(/-/g, '_') + '_' + idSuffix,
      nodeDesc: '',
      actionData: [],
      gatewayDirection: 'Diverging',
      ...(type === 'end-event' ? { terminationScope: scope } : {})
    }
  }
}

function getClosestAnchor(anchors, point) {
  return (anchors || []).reduce((closest, anchor) => {
    const distance = Math.pow(anchor.x - point.x, 2) + Math.pow(anchor.y - point.y, 2)
    if (!closest || distance < closest.distance) return { anchor, distance }
    return closest
  }, null)
}

function validateConnection(sourceNode, targetNode, sourceAnchor, targetAnchor) {
  const sourceResult = sourceNode.isAllowConnectedAsSource(targetNode, sourceAnchor, targetAnchor)
  if (!sourceResult.isAllPass) return sourceResult.msg || '该节点不允许作为连线起点'
  const targetResult = targetNode.isAllowConnectedAsTarget(sourceNode, sourceAnchor, targetAnchor)
  if (!targetResult.isAllPass) return targetResult.msg || '该节点不允许作为连线终点'
  return ''
}

export function addConnectedNode(lf, { sourceNode, sourceAnchor, type, direction, terminationScope, edgeType }) {
  const graph = lf.getGraphData() || {}
  const position = findAvailableNodePosition(graph.nodes, sourceNode, direction)
  const targetNode = lf.addNode(createFlowNodeData(type, { ...position, terminationScope }))
  try {
    const closest = getClosestAnchor(targetNode.anchors, sourceAnchor)
    if (!closest) throw new Error('目标节点缺少可连接锚点')
    const message = validateConnection(sourceNode, targetNode, sourceAnchor, closest.anchor)
    if (message) throw new Error(message)
    const edge = lf.addEdge({
      ...(edgeType ? { type: edgeType } : {}),
      sourceNodeId: sourceNode.id,
      targetNodeId: targetNode.id,
      sourceAnchorId: sourceAnchor.id,
      targetAnchorId: closest.anchor.id
    })
    lf.selectElementById(targetNode.id)
    return { node: targetNode, edge }
  } catch (error) {
    lf.deleteNode(targetNode.id)
    throw error
  }
}

export function calculateGroupBounds(models, padding = 40) {
  if (!models || models.length === 0) return null
  const bounds = models.map(model => {
    if (typeof model.getBounds === 'function') return model.getBounds()
    const halfWidth = (model.width || 0) / 2
    const halfHeight = (model.height || 0) / 2
    return {
      minX: model.x - halfWidth,
      maxX: model.x + halfWidth,
      minY: model.y - halfHeight,
      maxY: model.y + halfHeight
    }
  })
  const minX = Math.min(...bounds.map(item => item.minX)) - padding
  const maxX = Math.max(...bounds.map(item => item.maxX)) + padding
  const minY = Math.min(...bounds.map(item => item.minY)) - padding
  const maxY = Math.max(...bounds.map(item => item.maxY)) + padding
  return {
    x: (minX + maxX) / 2,
    y: (minY + maxY) / 2,
    width: maxX - minX,
    height: maxY - minY
  }
}

export function createDynamicGroup(lf) {
  const selected = lf.getSelectElements(true) || {}
  const dynamicGroup = lf.extension.dynamicGroup
  const models = (selected.nodes || [])
    .map(node => lf.getNodeModelById(node.id))
    .filter(model => model && !model.isGroup && !dynamicGroup.nodeGroupMap.has(model.id))
  if (models.length < 2) throw new Error('请至少选择两个尚未分组的节点')
  const bounds = calculateGroupBounds(models)
  const children = models.map(model => model.id)
  const group = lf.addNode({
    type: 'dynamic-group',
    x: bounds.x,
    y: bounds.y,
    text: '分组',
    properties: {
      children,
      width: bounds.width,
      height: bounds.height,
      collapsible: true,
      isRestrict: false,
      autoResize: true
    }
  })
  lf.clearSelectElements()
  lf.selectElementById(group.id)
  return group
}

export function getPersistableGraphData(lf) {
  const graph = lf.getGraphData() || {}
  const virtualEdgeIds = new Set((lf.graphModel.edges || []).filter(edge => edge.virtual).map(edge => edge.id))
  return {
    ...graph,
    nodes: graph.nodes || [],
    edges: (graph.edges || []).filter(edge => !virtualEdgeIds.has(edge.id))
  }
}

export function getBusinessGraphData(graph) {
  const nodes = (graph.nodes || []).filter(node => node.type !== 'dynamic-group' && !(node.properties && node.properties.isGroup))
  const nodeIds = new Set(nodes.map(node => node.id))
  return {
    nodes,
    edges: (graph.edges || []).filter(edge => nodeIds.has(edge.sourceNodeId) && nodeIds.has(edge.targetNodeId))
  }
}

function designerNodeName(node) {
  return (node.properties && node.properties.nodeName) || node.id
}

function edgeHasCondition(edge) {
  const properties = edge.properties || {}
  if (String(properties.conditionExpr || '').trim()) return true
  return hasUsableConditionLeaf(properties.conditionConfig)
}

export function validateDesignerGraphStructure(graph, { treeMode = false } = {}) {
  const nodes = (graph && graph.nodes) || []
  const edges = (graph && graph.edges) || []
  const errors = []
  const starts = nodes.filter(node => node.type === 'start-event')
  if (starts.length === 0) errors.push('缺少开始节点')
  if (starts.length > 1) errors.push('开始节点只能有一个')
  const incoming = new Map(nodes.map(node => [node.id, []]))
  const outgoing = new Map(nodes.map(node => [node.id, []]))
  edges.forEach(edge => {
    if (outgoing.has(edge.sourceNodeId)) outgoing.get(edge.sourceNodeId).push(edge)
    if (incoming.has(edge.targetNodeId)) incoming.get(edge.targetNodeId).push(edge)
  })

  nodes.forEach(node => {
    const inputEdges = incoming.get(node.id) || []
    const outputEdges = outgoing.get(node.id) || []
    const name = designerNodeName(node)
    if (node.type === 'start-event') {
      if (inputEdges.length) errors.push(`开始节点「${name}」不能有入边`)
      if (outputEdges.length !== 1)
        errors.push(`节点「${name}」必须且只能有一条出边`)
    } else if (node.type === 'script-task' || node.type === 'join-gateway') {
      if (outputEdges.length !== 1)
        errors.push(`节点「${name}」必须且只能有一条出边`)
    } else if (node.type === 'end-event') {
      if (outputEdges.length) errors.push(`结束节点「${name}」不能有出边`)
    } else if (node.type === 'exclusive-gateway') {
      if (outputEdges.length < 2)
        errors.push(`条件判断节点「${name}」至少需要两个出口`)
      const defaults = outputEdges.filter(edge => !edgeHasCondition(edge))
      if (defaults.length > 1)
        errors.push(`条件判断节点「${name}」最多只能有一个默认分支`)
    }
    if (treeMode && node.type === 'join-gateway')
      errors.push(`决策树不允许使用聚合节点「${name}」，请使用决策流`)
    if (!treeMode && node.type === 'join-gateway' && inputEdges.length < 2)
      errors.push(`聚合节点「${name}」至少需要两个入边`)
    if (treeMode && node.type !== 'start-event' && inputEdges.length > 1)
      errors.push(`节点「${name}」有多条入边，决策树不允许分支汇合`)
  })

  const nodeIds = nodes.map(node => node.id)
  if (graphContainsDirectedCycle(edges, nodeIds))
    errors.push(`${treeMode ? '决策树' : '决策流'}不允许存在有向环路`)
  if (starts.length === 1) {
    const reachable = new Set()
    const queue = [starts[0].id]
    while (queue.length) {
      const id = queue.shift()
      if (reachable.has(id)) continue
      reachable.add(id)
      ;(outgoing.get(id) || []).forEach(edge => queue.push(edge.targetNodeId))
    }
    const unreachable = nodes.filter(node => !reachable.has(node.id))
    if (unreachable.length)
      errors.push(`存在从开始节点不可达的节点：${unreachable.map(designerNodeName).join('、')}`)
    if (!nodes.some(node => reachable.has(node.id) && node.type === 'end-event'))
      errors.push('缺少从开始节点可达的结束节点')
  }
  return errors
}
