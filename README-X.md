<!-- This file is rendered by https://github.com/BlvckBytes/readme_helper -->

# BukkitInventoryUI

A collection of UI templates for rapid development, making use of [BukkitEvaluable](https://github.com/BlvckBytes/BukkitEvaluable)
(take a look at it's section descriptions as well).

<!-- #toc -->

## Configuration Sections

Each template comes with it's own configuration section. Sections can extend other sections and
thus inherit all of their properties.

### Base UI Layout

| Key             | Type                          | Description                                                          |
|-----------------|-------------------------------|----------------------------------------------------------------------|
| numberOfRows    | Integer                       | How many rows to display (if applicable)                             |
| title           | String                        | Inventory title to display                                           |
| animating       | Boolean                       | Whether to play animations (if applicable)                           |
| animationPeriod | Integer                       | At how many ticks to advance the animation (if applicable)           |
| slotContents    | Map<String, List<Integer>>    | Mapping predefined slot names (key) to slot IDs (value)              |
| customItems     | Map<String, ItemStackSection> | Mapping custom slot names to custom items to be used in slotContents |

```yaml
numberOfRows: 5

title: '&6Hello, world!'

animating: true

animationPeriod: 3 # advance the animation every three ticks

customItems:
  # Defining a black glass pane as a custom item
  darkGlass:
    type: BLACK_STAINED_GLASS_PANE
    name: ' '
    
slotContents:
  filter: 21
  back: 22
  # Slots can be either single IDs
  searchItem: 23
  # Or a list of IDs to set the item to multiple slots
  darkGlass$: |
    flatten(
      range(0, 8),
      list_of(9, 18, 27, 17, 26, 35),
      range(36, 44)
    )
```

### Pageable UI

| Key             | Type             | Description                            |
|-----------------|------------------|----------------------------------------|
| previousPage    | ItemStackSection | Previous page item description         |
| currentPage     | ItemStackSection | Current page item description          |
| nextPage        | ItemStackSection | Next page item description             |
| paginationSlots | List<Integer>    | List of slots used for paginated items |

```yaml
previousPage:
  type: PLAYER_HEAD
  textures$: '...'
  name$: '...'

currentPage:
  type: PAPER
  name$: '...'
  lore$: '...'

nextPage:
  type: PLAYER_HEAD
  textures$: '...'
  name$: '...'

paginationSlots$: |
  flatten(
    range(10, 16),
    range(19, 25),
    range(28, 34)
  )
```

### Anvil Search UI

Extends the Pageable UI section.

| Key                 | Type             | Description                                   |
|---------------------|------------------|-----------------------------------------------|
| filter              | ItemStackSection | Filter mode selector item description         |
| back                | ItemStackSection | Back button item description                  |
| searchItem          | ItemStackSection | Search placeholder item description           |
| resultItem          | ItemStackSection | Search result indicator item description      |
| searchDebounceTicks | Integer          | Minimum time in ticks between search requests |

```yaml
filter:
  type: HOPPER
  name: '...'

back:
  type: PLAYER_HEAD
  textures$: '...'

searchItem:
  type: PURPLE_TERRACOTTA
  # The name should always be blank in order to allow for an empty input box
  name: ' '

resultItem:
  type: PURPLE_TERRACOTTA
  name: '...'

searchDebounceTicks: 5
```

### Single Choice UI

Extends the Pageable UI section.

| Key    | Type             | Description                          |
|--------|------------------|--------------------------------------|
| search | ItemStackSection | Start search button item description |

```yaml
search:
  type: NAME_TAG
  name: '...'
```