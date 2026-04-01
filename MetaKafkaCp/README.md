# MetaKafkaCp

Минимальное Java 21 приложение для пакетного копирования сообщений из одного Kafka-кластера в другой.

## Что делает

- Читает все сообщения из исходного топика с начала.
- Копирует ключ, значение, timestamp и headers.
- Пишет сообщения в целевой топик другого Kafka-кластера.
- Завершается после того, как дочитает конец топика на момент старта.

## Требования

- JDK 21+
- Доступ к обоим Kafka-кластерам

## Конфиг

Основной конфиг: `config/application.properties`

Обязательные параметры:

- `app.source.bootstrapServers`
- `app.source.topic`
- `app.target.bootstrapServers`
- `app.target.topic`

Дополнительные Kafka-свойства можно прокинуть напрямую:

- consumer: `app.source.consumer.<kafka_property>=...`
- producer: `app.target.producer.<kafka_property>=...`

Это позволяет задавать SASL, SSL, таймауты, ретраи и другие стандартные настройки клиента Kafka без изменения кода.

## Запуск

```powershell
.\gradlew.bat run
```

Или с явным путём к конфигу:

```powershell
.\gradlew.bat run -PappArgs="C:\path\to\application.properties"
```

## Сборка

```powershell
.\gradlew.bat build
```
