## Документация плагина WebServerPlugin

### Описание
Плагин предоставляет веб-интерфейс для управления Minecraft-сервером через REST API. Позволяет выполнять команды консоли, получать информацию об игроках и управлять сервером через HTTP-запросы.

### Установка
1. Поместите JAR-файл плагина в папку plugins
2. Перезапустите сервер
3. Настройте конфигурационные файлы

### Конфигурация

#### Основной конфиг (config.yml)
```yaml
server:
  port: 8080  # Порт для веб-сервера
```

#### Маршруты (routes.yml)
```yaml
routes:
  - path: /api/players
    allow: [GET]
    auth: true
  - path: /static
    dir: web
    index: index.html
    allow: [GET]
```

#### Ключи доступа (keys.yml)
```yaml
keys:
  - your_api_key_here
  - another_key
```

### API эндпоинты

#### /server/console/execute
**Метод:** POST  
**Параметры:**
* command - команда для выполнения

**Пример запроса:**
```
GET /server/console/execute?command=say Hello%20World
```

#### /api/players
**Метод:** GET  
**Ответ:**
```json
{
  "success": true,
  "count": 5,
  "players": ["Player1", "Player2"]
}
```

#### /api/player/inventory
**Метод:** GET  
**Параметры:**
* name - имя игрока

**Ответ:**
```json
{
  "success": true,
  "player": "Player1",
  "inventory": [
    {"slot": 0, "id": 264, "damage": 0, "count": 64}
  ]
}
```

#### /api/player/isadmin
**Метод:** GET  
**Параметры:**
* name - имя игрока

**Ответ:**
```json
{
  "success": true,
  "player": "Player1",
  "isAdmin": true
}
```

### Авторизация
* Через заголовок Authorization: Bearer YOUR_KEY
* Через параметр запроса ?key=YOUR_KEY

### Управление плагином
**Консольная команда:**
```
/webserver on|off
```

### Структура папок
```
plugins/WebServerPlugin/
├── config.yml
├── routes.yml
├── keys.yml
└── web/  # папка для статических файлов
```

### Требования
* Nukkit/PocketMine-сервер
* Java 8 или выше
* Права на запуск веб-сервера на указанном порту

### Ошибки и статусы
* 401 - Неавторизованный доступ
* 403 - Запрещено
* 404 - Ресурс не найден
* 405 - Метод не разрешен
* 500 - Внутренняя ошибка сервера
