# 🔍 Detectando Pressão de F no Bloco

## Descoberta Importante
Quando o jogador pressiona **F** em um bloco (como o Helm), o evento disparado é `LivingEntityUseBlockEvent` e **NÃO** `UseBlockEvent`.

## Timeline da Investigação

### ❌ O que NÃO funciona
- `UseBlockEvent.Pre` - Não dispara para interações de bloco tipo "Seat"
- `UseBlockEvent.Post` - Não dispara para interações de bloco tipo "Seat"
- `EventPriority.LOWEST` - Nem existe! Use `EventPriority.NORMAL`

### ✅ O que FUNCIONA
- **`LivingEntityUseBlockEvent`** - Dispara quando F é pressionado em qualquer bloco interativo
- **`EventPriority.NORMAL`** - Prioridade padrão correta para listeners

## Código de Implementação

### 1. Adicionar o Import
```java
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityUseBlockEvent;
```

### 2. Registrar o Listener
```java
getEventRegistry().registerGlobal(
    EventPriority.NORMAL,
    LivingEntityUseBlockEvent.class,
    event -> {
        try {
            String blockType = event.getBlockType();
            LivingEntity entity = event.getEntity();
            
            ShipLogger.success("🎯 LivingEntityUseBlockEvent disparado!");
            ShipLogger.info("    BlockType: " + blockType);
            ShipLogger.info("    Entity: " + entity.getName());
            
            // Processar o evento conforme necessário
            if (blockType != null && blockType.equalsIgnoreCase("Helm")) {
                ShipLogger.success("🧭 HELM FOI USADO!");
                // Fazer algo com o Helm
            }
        } catch (Exception e) {
            ShipLogger.error("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
);
```

## Estrutura do Evento

```java
public class LivingEntityUseBlockEvent extends Event {
    private final LivingEntity entity;      // Quem usou o bloco
    private final String blockType;        // Nome do bloco (ex: "Helm")
    private final Vector3i position;       // Posição do bloco no mundo
    private final InteractionContext ctx;  // Contexto da interação
}
```

## Métodos Úteis

### `event.getEntity()`
Retorna a entidade que pressionou F (pode ser jogador)
```java
LivingEntity entity = event.getEntity();
UUID playerUUID = entity.getUUID();
String playerName = entity.getName();
```

### `event.getBlockType()`
Retorna o ID do bloco como String
```java
String blockId = event.getBlockType();
boolean isHelm = blockId.equalsIgnoreCase("Helm");
```

### `event.getPosition()` (se disponível)
Posição do bloco no mundo
```java
Vector3i pos = event.getPosition();
int x = pos.getX();
int y = pos.getY();
int z = pos.getZ();
```

## Estrutura de Blocos Interativos

### Blocos Confirmados com F
- ✅ **Helm** - Controle de navio
- ✅ **Chairs** (4 variações) - Assentos
- ✅ **Furnace** - Queimador
- ✅ **Mill** - Moedor
- ✅ **Lantern** - Lanterna interativa

### Arquivo JSON de Bloco
Localização: `HytaleAssets/Server/Block/Helm.json`

```json
{
  "Block": {
    "Id": "Helm",
    "Interactions": {
      "Use": "Block_Seat"
    }
  }
}
```

- Campo `Use` referencia um `RootInteraction` chamado `Block_Seat`
- RootInteraction está em: `HytaleAssets/Server/Item/RootInteractions/Block/Block_Seat.json`

## Log de Teste Bem-Sucedido

```
[2026/01/18 01:04:54 INFO] [Ships] LivingEntityUseBlockEvent disparado!
[2026/01/18 01:04:54 INFO] [Ships]     BlockType: Helm
[2026/01/18 01:04:54 INFO] [Ships] HELM FOI USADO!
```

## Próximos Passos

1. ✅ Detectar evento de F pressionado → COMPLETO
2. ⏳ Rastrear qual jogador usou o Helm
3. ⏳ Sincronizar movimento do navio
4. ⏳ Implementar controles de movimento (W/A/S/D)
5. ⏳ Sistema de passageiros no navio

## Referências

- **Classe**: `com.hypixel.hytale.server.core.event.events.entity.LivingEntityUseBlockEvent`
- **Localização no Source**: `HytaleServer.jar.src/com/hypixel/hytale/server/core/event/events/entity/LivingEntityUseBlockEvent.java`
- **Framework**: Hytale Plugin API com EventRegistry
- **Priority Enum**: `com.hypixel.hytale.server.core.event.EventPriority`

## Notas Importantes

⚠️ **EventPriority.LOWEST não existe!** - Use apenas:
- `EventPriority.NORMAL`
- `EventPriority.HIGH`
- `EventPriority.HIGHEST`

⚠️ **UseBlockEvent não funciona para blocos tipo "Seat"** - Sempre use `LivingEntityUseBlockEvent` para interações gerais de bloco.

⚠️ **Requisito de Plugin.xml** - Certifique-se de que o plugin está carregado na inicialização do servidor para registrar listeners.
