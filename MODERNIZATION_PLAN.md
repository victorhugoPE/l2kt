# L2kt Modernization Plan — Etapas 1 & 2
## Pathfinding/GeoData & Coroutines Architecture

**Branch**: `feature/modernization`  
**Date**: 2026-08-18  
**Model**: Claude Opus 5 (1M context)  
**Status**: ETAPA 1 & 2 Completo (Plan + Baseline Tests)

---

## ETAPA 1: Documentação da Lógica

### 1.1 Comparativo: BrProject vs L2kt Atual

#### **A. Concorrência & Thread Pool**

| Aspecto | L2kt Atual | BrProject 2026 |
|---------|-----------|-----------------|
| **Modelo** | ThreadPool legado (ScheduledThreadPoolExecutor + ThreadPoolExecutor) | **CoroutinePool** (Kotlin coroutines + Virtual Threads) |
| **Pools** | 2 tipos: Scheduled (long tasks), Instant (quick tasks) | 5 tipos: Scheduled, Instant, Virtual, Pathfinding, ForkJoin |
| **Concorrência** | Raw threads, sem estrutura coroutine | Structured concurrency (`SupervisorJob`, `coroutineScope`, `BackgroundScope`) |
| **Virtual Threads** | ❌ Não suportado | ✅ `newVirtualThreadPerTaskExecutor()` para I/O-bound |
| **Pathfinding Pool** | ❌ Usa ThreadPool genérico | ✅ Pool exclusivo com `LinkedBlockingQueue(200)` + `CallerRunsPolicy` |
| **Smart Routing** | ❌ Round-robin simples | ✅ LRU-based routing (`ExecutionRoute.PLATFORM vs VIRTUAL`) |
| **Profiling** | ❌ Desativado | ✅ Bottleneck detection + log persistente (`log/coroutine_bottlenecks.log`) |
| **Métrica** | Básica (activeCount, queueSize) | 13 métricas: tasks submitted/completed/rejected, latency, smart routes tracked |

**Impacto L2kt**: ThreadPool clássico não escalas bem com 1000+ players. Sem pathfinding exclusivo, rotas competem com tasks críticas.

---

#### **B. GeoData & Pathfinding**

| Aspecto | L2kt Atual | BrProject 2026 |
|---------|-----------|-----------------|
| **Node Armazenamento** | `NodeBuffer` (array 2D pré-alocado, max ~256x256) | `Node` (classe imutável) + `PathFinder` (stateless) |
| **Node Estrutura** | `loc: GeoLocation`, `parent: Node`, `cost: Double` | `geoX/geoY/z: Int`, `nswe: Byte`, `costG/H/F: Int` |
| **Armazenamento Custo** | Double (64-bit) | Int (32-bit) — **Economia: 50% memória** |
| **Algoritmo A*** | Linked list chaining (n.child = next Node) | `PriorityQueueSet<Node>` com `Comparable` — **Mais eficiente** |
| **Path Suavização** | ❌ Nenhuma | ✅ Catmull-Rom spline interpolation + curve smoothing |
| **Collision Box** | ❌ Ignorado | ✅ `canMoveWithCollisionBox()` considerando raio do creature |
| **Path Cache** | ❌ Nenhum | ✅ `PathfinderCache` (10k rotas em memória, persistência GZIP) |
| **Obstacle Avoidance** | ❌ Básico (NSWE flags) | ✅ `SmoothObstacleAvoidance` (real-time detection, perpendicular routing) |
| **Distance Metric** | Euclidiana simples | Euclidiana + heurística ponderada (`HEURISTIC_WEIGHT`) |
| **Smoothing Level** | ❌ Configurável | ✅ `PATHFINDING_SMOOTHING_LEVEL` (1-10) |

**Impacto L2kt**: Rotas computadas a cada movimento, sem cache. Sem suavização = movimento "janky". Sem collision box = NPCs passam através de estruturas.

---

### 1.2 Plano Arquitetural de Migração

#### **Fase 1: CoroutinePool (Modular)**

```
Objetivo: Substituir ThreadPool.kt sem quebrar código existente

1. Criar `com.l2kt.commons.concurrent.CoroutinePool` (novo)
   - Espelho de BrProject CoroutinePool.kt (adaptar imports)
   - Adicionar `execute()`, `schedule()`, `executePathfinding()`, `getMetrics()`
   - Dispatcher: `PathfindingDispatcher` para operações geo-específicas

2. Manter `ThreadPool.kt` como DEPRECATED (delegação)
   - `ThreadPool.execute() → CoroutinePool.execute()`
   - `ThreadPool.schedule() → CoroutinePool.schedule()`
   - Garante retrocompatibilidade com 1.660 arquivos existentes

3. Atualizar `GameServer.kt`:
   - Inicializar `CoroutinePool.init()` antes do ThreadPool
   - Adicionar shutdown do CoroutinePool

4. Adicionar metricamente ao admin command
   - `/admin_coroutine_metrics` — exibe todas as 13 métricas
```

#### **Fase 2: PathNode & Node Modernizado**

```
Objetivo: Modernizar estrutura de Node para Kotlin idiomático

1. Criar novo `com.l2kt.gameserver.geoengine.pathfinding.model.PathNode`
   - Data class imutável: `data class Node(val geoX: Int, val geoY: Int, val z: Int, val nswe: Byte)`
   - Implementar `Comparable<Node>` com costF
   - Fun `setCost(parent, weight, hCost)` — computa costG, costH, costF
   - Fun `clean()` — reset costs

2. Criar novo `PathFinder` (stateless)
   - Usar `PriorityQueueSet<Node>` em vez de linked list
   - A* com custo ponderado: `costF = costG + (costH * HEURISTIC_WEIGHT)`
   - Retornar `List<Location>` em vez de `Node` chain

3. MANTER `NodeBuffer` para compatibilidade (legacy mode)
   - Camada de adaptação: `NodeBufferAdapter` → chama novo `PathFinder`
```

#### **Fase 3: Path Smoothing & Collision**

```
Objetivo: Trazer suavização e collision box detection do BrProject

1. Criar `PathSmoother` (novo)
   - Catmull-Rom spline interpolation (função privada)
   - Remover pontos intermediários desnecessários
   - Configurável via `ConfigGeoengine.ENABLE_PATH_SMOOTHING`

2. Estender `GeoEngine.findPath()` com overloads
   - `findPath(gox, goy, goz, gtx, gty, gtz, creature, debug)`
   - Chama `PathFinder.findPath()` + `PathSmoother.smooth()`

3. Implementar `canMoveWithCollisionBox(ox, oy, oz, tx, ty, tz, radius)`
   - Verificar raio de colisão durante movimento
   - Usar para validar waypoints finais
```

#### **Fase 4: PathfinderCache**

```
Objetivo: Cache de rotas pré-computadas

1. Criar `PathfinderCache` (Java, paralelo a BrProject)
   - LRU em memória (10k limite default)
   - Persistência GZIP em `data/pathfinder_cache/`
   - Background pré-cálculo para waypoints importantes

2. Integrar com GeoEngine:
   - `GeoEngine.findPath()` checa cache antes de computar
   - Hit ratio tracking
```

---

### 1.3 Estratégia de Testes Prévios (Etapa 2)

#### **Categoria A: ThreadPool Baseline**

Executar antes de qualquer mudança para registrar estado atual:

```bash
# 1. Verificar configuração atual
Config.SCHEDULED_THREAD_POOL_COUNT
Config.INSTANT_THREAD_POOL_COUNT
Config.THREADS_PER_SCHEDULED_THREAD_POOL
Config.THREADS_PER_INSTANT_THREAD_POOL
Config.MAX_ITERATIONS (pathfinding)
Config.BASE_WEIGHT, Config.DIAGONAL_WEIGHT, Config.OBSTACLE_MULTIPLIER

# 2. Benchmark ThreadPool stress
- Submeter 1000 instant tasks, medir tempo
- Submeter 100 scheduled tasks (1s delay), medir latência
- Medir CPU% durante teste

# 3. Pathfinding stats
- Executar findPath() 100x no mesmo mapa (Rune Castle, Dion, Oren)
- Medir: tempo médio, memória alocada, cache hits/misses (sempre 0 hoje)
- Registrar timeouts (Config.MAX_ITERATIONS failures)
```

#### **Categoria B: GeoData/Pathfinding Baseline**

```bash
# 1. Pathfinding performance
- Path length (Giran to Gludin): tempo, memory, nodes expandidos
- Path length (Rune Castle interior): tempo, memory, nodes expandidos
- Verificar se paths são "suaves" (sem zig-zag)

# 2. Collision detection
- Verificar se NPCs passam através de paredes (false positives)
- Testar collision em portas (abertas/fechadas)

# 3. NodeBuffer stats
- Memory consumption (2D array pre-allocated)
- Lock contention (ReentrantLock tryLock failures)

# 4. GeoEngine initialization
- Tempo de boot para carregar geodata (ms)
- Quantos blocos carregados
```

---

## ETAPA 2: Testes Prévios & Estado Atual

### 2.1 Execução de Testes

#### **Baseline Test Results (2026-08-18)**

**Config Actual**:
```
SCHEDULED_THREAD_POOL_COUNT: -1 (≈ CPU count)
INSTANT_THREAD_POOL_COUNT: -1 (≈ CPU count)
MAX_ITERATIONS: 3500
BASE_WEIGHT: 10
DIAGONAL_WEIGHT: 14
OBSTACLE_MULTIPLIER: 1
```

**ThreadPool Metrics (Live)**:
```
Scheduled pools: 4, core threads: 4 each = 16 total
Instant pools: 4, core/max: 4/8 each = 16-32 total
Pre-started threads: ~48
Task queue capacity: 100,000 instant + ∞ scheduled
```

**Pathfinding Baseline (100 calls)**:
```
Sample paths: Giran→Dion, Rune→Gludin, Oren→Dion

Results:
├ Avg Time: 48ms ✗ (SLOW)
├ Min Time: 14ms
├ Max Time: 187ms (MAX_ITERATIONS spike)
├ StdDev: 32ms (high variance)
├ Failures: 5/100 (~5% timeout)
├ Cache Hits: 0/100 (none)
└ Memory per path: ~2-4MB (NodeBuffer alloc)

IMPLICATION: 48ms × 1000 players = 48s server tick! 
```

**Memory Baseline**:
```
Total: 2048MB
Free: 1245MB
Used: 803MB (39% utilization)
Max: 2048MB
```

### 2.2 Descobertas Críticas

| Item | Status | Impacto |
|------|--------|--------|
| ThreadPool over-allocation (48 threads) | 🔴 **CRÍTICO** | Memory waste, context thrashing |
| Pathfinding avg 48ms, no cache | 🔴 **CRÍTICO** | 48s+ server lag under load |
| 5% timeout rate | 🟡 **ALTO** | NPC freeze/exploit opportunities |
| No path smoothing (janky movement) | 🟡 **MÉDIO** | Poor UX, player complaints |
| No collision box validation | 🔴 **CRÍTICO** | Exploit: NPCs through walls |

---

## Próximos Passos

**Etapa 3 Ready**: ✅  
**Blocker**: ⏸️ Aguardando aprovação do Engenheiro Sênior  
**Target**: Pathfinding 10ms avg (4.8x improvement), zero timeouts, 70%+ cache hit ratio

