# MOVEMENT_DEBUG.md — Diagnóstico Severity 1: Zig-Zag e Chão Sumindo

**Branch**: `feature/modernization`  
**Sintomas**: Player anda em zig-zag severo; chão "some" (Z incorreto)  
**Root Cause**: Incompatibilidade entre formato L2OFF (4-bit NSWE) e lógica de movimento L2D (8-bit NSWE + compound diagonals)

---

## 1. Causa Matemática: Chão Sumindo (Eixo Z)

### Parsing dos Blocos (CORRETO)

No formato L2OFF (`_conv.dat`), a altura é codificada dentro de um Short:

```
Short data = buffer.getShort()  // 16 bits
NSWE   = data & 0x000F          // bits 0-3 (4 bits, cardinal only)
Height = (data & 0xFFF0) >> 1   // bits 4-15, shifted right
```

O armazenamento e leitura no L2kt (BlockComplex/BlockMultilayer) estão **matematicamente corretos** — idênticos ao BrProject.

### Causa Real do Z Sumindo

O `GeoEngine.checkMove()` (linha 1204) atualiza o Z do pointer:

```kotlin
gpz = getHeightNearest(nx, ny, gpz).toInt()
```

Quando o movimento diagonal é **bloqueado** pelo bug dos compound flags (seção 2 abaixo), o servidor força o player a mover-se em zig-zag cardinal. Cada step cardinal busca um novo Z via `getHeightNearest`. Como o player está se movendo em um padrão não-natural (degraus em vez de diagonal), a sequência de Z's retornada pula entre heights de células adjacentes que podem ter alturas bem diferentes — causando o efeito de "chão sumindo".

**Conclusão**: O bug de Z é um **efeito colateral** do zig-zag. O parsing dos blocos está correto. Corrigir o zig-zag resolve o Z.

---

## 2. Causa Lógica: Zig-Zag no Pathfinding

### O Problema Central

O L2kt `GeoStructure` define 8 flags direcionais (bits 0-7):

```kotlin
CELL_FLAG_E  = 0x01  // bit 0
CELL_FLAG_W  = 0x02  // bit 1
CELL_FLAG_S  = 0x04  // bit 2
CELL_FLAG_N  = 0x08  // bit 3
CELL_FLAG_SE = 0x10  // bit 4  ← NÃO EXISTE EM L2OFF!
CELL_FLAG_SW = 0x20  // bit 5  ← NÃO EXISTE EM L2OFF!
CELL_FLAG_NE = 0x40  // bit 6  ← NÃO EXISTE EM L2OFF!
CELL_FLAG_NW = 0x80  // bit 7  ← NÃO EXISTE EM L2OFF!
```

O formato L2OFF usa **apenas 4 bits**: `data & 0x000F` → apenas E, W, S, N (bits 0-3).  
O valor máximo possível de NSWE em L2OFF é `0x0F` (todas 4 cardinais livres).

BrProject define: `CELL_FLAG_ALL = 0x0F` (4 bits máximo).

### Cadeia de Falha no `checkMove()`

```
1. Player quer mover na diagonal (ex: NorthWest)
2. GeoEngine.checkMove() calcula via Bresenham que e2 > -dy && e2 < dx
3. direction = getDirXY(CELL_FLAG_W, CELL_FLAG_N) → retorna CELL_FLAG_NW (0x80)
4. getNsweNearest() retorna 0x0F (máximo L2OFF = todas 4 cardinais livres)
5. Check: (0x0F & 0x80) == 0 → TRUE → MOVEMENT BLOCKED!
6. checkMove() retorna a posição atual como "última posição válida"
7. Servidor impede a diagonal, player caminha apenas em N ou W
8. Resultado: zig-zag (staircase pattern)
```

### Cadeia de Falha no `NodeBuffer.expand()`

```kotlin
// Legacy pathfinder verifica diagonais com compound flags:
if ((nswe.toInt() and GeoStructure.CELL_FLAG_NW.toInt()) != 0)  // 0x80
    addNode(x - 1, y - 1, z, Config.DIAGONAL_WEIGHT)

// NSWE de L2OFF = 0x0F no máximo
// 0x0F & 0x80 = 0 → DIAGONAL NUNCA É ADICIONADA ao path
```

### Por que o AdvancedPathFinder NÃO sofre esse bug

O `AdvancedPathFinder` usa **corner validation** em vez de compound flags:

```kotlin
// Verifica se o vizinho W permite ir N, E se o vizinho N permite ir W
if ((nsweX.toInt() and dirFlagY.toInt()) != 0 && (nsweY.toInt() and dirFlagX.toInt()) != 0)
```

Aqui `dirFlagY = CELL_FLAG_N (0x08)` e `dirFlagX = CELL_FLAG_W (0x02)` — ambos são bits 0-3, que EXISTEM nos dados L2OFF. **Isso funciona corretamente.**

**Porém**: O problema é que o sistema de MOVIMENTO DO PLAYER não usa o AdvancedPathFinder. Ele usa `GeoEngine.checkMove()` diretamente para validar cada step do movimento. E `checkMove()` está bugado.

---

## 3. Como o BrProject Resolve

O BrProject usa um sistema completamente diferente para validação de movimento diagonal:

1. **`MoveDirectionType` enum** — decompõe a direção em componentes X e Y independentes
2. **Grid-stepping** — em vez de Bresenham com compound flags, caminha pelo grid verificando cardinais separadamente
3. **Verificação decomposta**: para diagonal, verifica `(nswe & dirX) != 0` na célula atual, depois `(nswe & dirY) != 0` na célula após o step X

O check nunca usa compound flags (NW/NE/SE/SW). Sempre decompõe em 2 verificações cardinais.

---

## 4. Mapeamento EXATO de Classes para Correção

| # | Classe L2kt | Método/Área | Ação | Prioridade |
|---|-------------|-------------|------|-----------|
| 1 | `GeoEngine.kt` | `checkMove()` (L1146-1210) | **REESCREVER** — decompor diagonal em 2 checks cardinais | **P0** |
| 2 | `GeoEngine.kt` | `getDirXY()` (L295-303) | **REMOVER** uso em checks de passabilidade | **P0** |
| 3 | `GeoEngine.kt` | `canSeeTarget()` (L606-780) | **REESCREVER** seção diagonal | **P1** |
| 4 | `GeoEngine.kt` | `canSeeTargetOriginal()` (L800-950) | **REESCREVER** seção diagonal | **P1** |
| 5 | `NodeBuffer.kt` | `expand()` (L152-182) | **CORRIGIR** — corner validation | **P1** |
| 6 | `GeoStructure.kt` | Constantes | **ADICIONAR** `CELL_FLAG_ALL=0x0F`, `CELL_FLAG_NONE=0x00` | **P2** |
| 7 | `PathSmoother.kt` | `canMoveDirectly()` | **Sem mudança** (depende de checkMove fix) | **P3** |
| 8 | `AdvancedPathFinder.kt` | Corner validation | **Sem mudança** (já correto) | — |
| 9 | `BlockComplex.kt` | Parsing | **Sem mudança** (correto) | — |
| 10 | `BlockMultilayer.kt` | Parsing | **Sem mudança** (correto) | — |
| 11 | `BlockFlat.kt` | Parsing | **Sem mudança** (correto) | — |

---

## 5. Fix Proposto para `checkMove()` (P0)

```kotlin
// ATUAL (bugado):
val dirXY = getDirXY(dirX, dirY)
// ...no loop:
if (e2 > -dy && e2 < dx) {
    direction = (direction.toInt() or dirXY.toInt()).toByte()  // compound flag!
}
// Check:
if ((getNsweNearest(gpx, gpy, gpz).toInt() and direction.toInt()) == 0)
    return GeoLocation(gpx, gpy, gpz)  // BLOCKED!

// FIX (decomposto):
if (e2 > -dy && e2 < dx) {
    // Diagonal: verificar X e Y separadamente
    val nsweHere = getNsweNearest(gpx, gpy, gpz)
    // Can move in X direction from current cell?
    if ((nsweHere.toInt() and dirX.toInt()) == 0)
        return GeoLocation(gpx, gpy, gpz)
    // Can move in Y direction from the X-neighbor?
    val nz = getHeightNearest(gpx + sx, gpy, gpz).toInt()
    val nsweNext = getNsweNearest(gpx + sx, gpy, nz)
    if ((nsweNext.toInt() and dirY.toInt()) == 0)
        return GeoLocation(gpx, gpy, gpz)
    // Both passed — diagonal is valid
    nx += sx
    ny += sy
    d -= dy
    d += dx
} else if (e2 > -dy) {
    // Cardinal X only
    if ((getNsweNearest(gpx, gpy, gpz).toInt() and dirX.toInt()) == 0)
        return GeoLocation(gpx, gpy, gpz)
    d -= dy
    nx += sx
} else if (e2 < dx) {
    // Cardinal Y only
    if ((getNsweNearest(gpx, gpy, gpz).toInt() and dirY.toInt()) == 0)
        return GeoLocation(gpx, gpy, gpz)
    d += dx
    ny += sy
}
// Update pointer
gpx = nx
gpy = ny
gpz = getHeightNearest(nx, ny, gpz).toInt()
```

---

## 6. Conclusão

| Bug | Causa Raiz | Complexidade do Fix |
|-----|-----------|-------------------|
| **Zig-zag** | `checkMove()` usa compound flags (bits 4-7) que L2OFF nunca seta | Reescrever checkMove (~30 linhas) |
| **Chão some** | Efeito cascata do zig-zag — Z's incorretos de steps cardinais forçados | Resolvido automaticamente pelo fix do zig-zag |

**O parsing dos blocos está CORRETO. O bug está na lógica de consumo dos NSWE flags.**

---

**Status**: ✅ Análise completa  
**Aguardando**: Autorização para implementar fixes P0 (checkMove + getDirXY)
