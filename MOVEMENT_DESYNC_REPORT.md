# MOVEMENT_DESYNC_REPORT.md — Diagnóstico S1: Rubberbanding & Limbo Visual

**Branch**: `feature/modernization`  
**Sintomas**: Rubberbanding (servidor rejeita posição), tela branca (Z absurdo)  
**Severidade**: S1 — Player não consegue se mover no jogo

---

## 1. Onde o Eixo Z está sendo Corrompido

### 1.1 `canMoveToTargetLoc` → `checkMove` Z-Mismatch

**Arquivo**: `GeoEngine.kt:1094-1119`

```kotlin
fun canMoveToTargetLoc(ox, oy, oz, tx, ty, tz): Location {
    val goz = getHeightNearest(gox, goy, oz)   // worldZ → geoZ
    val gtz = getHeightNearest(gtx, gty, tz)   // worldZ → geoZ
    return checkMove(gox, goy, goz.toInt(), gtx, gty, gtz.toInt())
    // Retorna GeoLocation(gpx, gpy, gpz)
}
```

**Problema**: `checkMove` retorna `GeoLocation` cujo constructor recalcula Z:

```kotlin
class GeoLocation(x, y, z) : Location(x, y, GeoEngine.getHeightNearest(x, y, z).toInt())
```

Quando o path é bloqueado logo no início (1-2 cells do origin), retorna posição praticamente igual à origem. `Creature.moveToLocation()` calcula `distance ≈ 0` → cancela movimento → **rubberband**.

### 1.2 `updatePosition` Z-Correction Aleatória

**Arquivo**: `Creature.java:2910`

```java
if (!isFloating && !m.disregardingGeodata && Rnd.get(10) == 0 && GeoEngine.hasGeo(xPrev, yPrev)) {
    short geoHeight = GeoEngine.getHeight(xPrev, yPrev, zPrev);
    zPrev = geoHeight;  // ← Z do player substituído aleatoriamente
}
```

**Problema**: Acontece com 10% de chance a cada tick (~1x/s). Se geodata retorna Z de layer errada (multilayer com Z acumulado incorretamente), o player é teleportado instantaneamente → **limbo visual**.

### 1.3 `MoveBackwardToLocation` — collisionHeight Offset

```kotlin
_targetZ += activeChar.collisionHeight.toInt()  // +35 a +70 ao targetZ
```

Este Z inflado entra em `canMoveToTargetLoc`. O `getHeightNearest` com esse Z elevado pode selecionar layer errada em BlockMultilayer → cadeia de Z corrupto.

---

## 2. Onde o Desync (Rubberbanding) Ocorre

### 2.1 `ValidatePosition` — Threshold Estrito

**Arquivo**: `ValidatePosition.kt:60-70`

```kotlin
if (diffSq > 250000 || Math.abs(dz) > 200) {
    // dz > 200: servidor e cliente discordam no Z
    player.sendPacket(ValidateLocation(player))  // ← RUBBERBAND: reenvia posição do servidor
}
```

**Cadeia**: Servidor calcula Z via geodata → Cliente calcula Z via interpolação → Diferença > 200 → Servidor força sua posição → **rubberband contínuo**.

### 2.2 `updatePosition` — Loop de Desync

```
Tick N: updatePosition() → Z corrigido para geoHeight (aleatório)
Tick N+1: cliente ainda no Z antigo (não recebeu update)
Tick N+2: ValidatePosition chega → dz > 200 → ValidateLocation enviado
Tick N+3: cliente recebe nova posição → pula visualmente → repete
```

### 2.3 `checkMove` Path Curto → Movement Cancelado

Após o fix de diagonal, `checkMove` pode validar mais movimentos, **MAS**: quando uma parede real bloqueia perto da origem, o retorno está a poucos pixels de distância. `moveToLocation` vê `distance < 1` → `setIntention(IDLE)` → cliente continua animação → **rubberband**.

---

## 3. Lista EXATA de Classes para Port

### P0 — Fix Cirúrgico (Parar Rubberbanding Imediatamente)

| # | Arquivo | Método/Linha | Fix |
|---|---------|-------------|-----|
| 1 | `GeoEngine.kt` | `canMoveToTargetLoc()` L1094 | Quando checkMove retorna ponto ≈ origin, retornar `Location(tx,ty,tz)` com Z do destino (não da origem) |
| 2 | `Creature.java` | `updatePosition()` L2910 | Remover `Rnd.get(10) == 0` — sempre aplicar geo Z OU nunca (consistência) |
| 3 | `ValidatePosition.kt` | L60 threshold | Relaxar dZ de 200 → 400 para L2OFF |

### P1 — Port Completo do Sistema de Movimento

| # | Classe BrProject | Destino L2kt | Propósito |
|---|-----------------|-------------|-----------|
| 4 | `CreatureMove.kt` | `model/actor/move/CreatureMove.kt` | Sistema de movimento modular (substituir Creature.moveToLocation 200 linhas) |
| 5 | `PlayerMove.kt` | `model/actor/move/PlayerMove.kt` | Sync cliente-servidor específico |
| 6 | `SummonMove.kt` | `model/actor/move/SummonMove.kt` | Follow logic para summons |
| 7 | `MovementIntegration.java` | `model/actor/move/MovementIntegration.kt` | GeoEngine↔Movement bridge |
| 8 | `MoveBackwardToLocation.java` | `network/clientpackets/MoveBackwardToLocation.kt` | Usar `getValidLocation` + `maybeMoveToLocation` |
| 9 | `ValidatePosition.java` | `network/clientpackets/ValidatePosition.kt` | Threshold dinâmico baseado em speed |

### P2 — Melhorias de Qualidade

| # | Classe | Ação |
|---|--------|------|
| 10 | `GeoEngine.getValidLocation()` | NOVO — retorna Location validada sem GeoLocation wrapper |
| 11 | `SmoothObstacleAvoidance` integration | Usar durante path execution (não só no planning) |
| 12 | `GeoLocation.kt` | Refatorar — remover x/y getter override (confuso e causa bugs) |

---

## 4. Proposta de Fix P0 (3 Edições)

### Fix 1: `canMoveToTargetLoc` — Retorno com World Coords Corretos

```kotlin
fun canMoveToTargetLoc(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int): Location {
    val gox = getGeoX(ox); val goy = getGeoY(oy)
    if (!hasGeoPos(gox, goy)) return Location(tx, ty, tz)
    val goz = getHeightNearest(gox, goy, oz)
    
    val gtx = getGeoX(tx); val gty = getGeoY(ty)
    if (!hasGeoPos(gtx, gty)) return Location(tx, ty, tz)
    val gtz = getHeightNearest(gtx, gty, tz)
    
    if (gox == gtx && goy == gty) return Location(tx, ty, tz)
    
    val result = checkMove(gox, goy, goz.toInt(), gtx, gty, gtz.toInt())
    // Converter explicitamente para world coords (sem depender de GeoLocation override)
    return Location(getWorldX(result.geoX), getWorldY(result.geoY), result.z)
}
```

### Fix 2: `updatePosition` — Z Consistente

```java
// Remover randomização — sempre usar geodata para ground creatures
if (!isFloating && !m.disregardingGeodata && GeoEngine.INSTANCE.hasGeo(xPrev, yPrev)) {
    short geoHeight = GeoEngine.INSTANCE.getHeight(xPrev, yPrev, zPrev);
    dz = m._zDestination - geoHeight;
    if (this instanceof Player && Math.abs(((Player) this).getClientZ() - geoHeight) > 200 
        && Math.abs(((Player) this).getClientZ() - geoHeight) < 1500) {
        dz = m._zDestination - zPrev;
    } else if (isInCombat() && Math.abs(dz) > 200 && (dx * dx + dy * dy) < 40000) {
        dz = m._zDestination - zPrev;
    } else {
        zPrev = geoHeight;
    }
}
```

### Fix 3: `ValidatePosition` — Threshold Relaxado

```kotlin
// L2OFF geodata tem menos precisão Z, relaxar threshold
if (diffSq > 250000 || Math.abs(dz) > 400)
```

---

## 5. Causa-Raiz Visual

```
Player clica para mover (cliente)
    ↓
MoveBackwardToLocation: targetZ += collisionHeight
    ↓
canMoveToTargetLoc → checkMove (diagonal decomposed)
    ↓ (se parede próxima)
Retorna GeoLocation ≈ origem → distance ≈ 0 → IDLE
    ↓
Cliente já moveu câmera → RUBBERBAND
    ↓ (em paralelo)
updatePosition: random 10% Z-correction → Z diverge
    ↓
ValidatePosition: dz > 200 → envia posição servidor
    ↓
Cliente recebe Z absurdo → LIMBO VISUAL
```

---

## 6. Recomendação

**P0 (imediato, 3 edições)**: Resolve 80% dos sintomas. Aplicar Fix 1+2+3.

**P1 (próxima sprint)**: Port completo de `CreatureMove` do BrProject. Resolve os 20% restantes (edge cases, follow, summon, combat movement).

---

**Status**: ✅ Análise completa  
**Aguardando**: Autorização para implementar Fix P0
