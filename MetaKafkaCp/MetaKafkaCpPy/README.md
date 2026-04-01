# MetaKafkaCpPy

Минимальное Python 3.13+ приложение для пакетного копирования сообщений из одного Kafka-кластера в другой.

## Что делает

- Читает все сообщения из исходного топика с начала.
- Фиксирует конец топика на момент старта и завершает работу после его достижения.
- Копирует key, value, headers и timestamp.
- Может сохранить номер партиции источника при записи в целевой топик.

## Установка

```powershell
pip install .
```

## Запуск

По умолчанию используется `config/application.properties`:

```powershell
meta-kafka-cp
```

Или с явным путём к конфигу:

```powershell
meta-kafka-cp C:\path\to\application.properties
```

## Конфиг

Основные параметры:

- `app.source.bootstrapServers`
- `app.source.topic`
- `app.source.groupId`
- `app.source.clientId`
- `app.source.securityProtocol`
- `app.target.bootstrapServers`
- `app.target.topic`
- `app.target.clientId`
- `app.target.acks`
- `app.target.securityProtocol`

Для SASL/SSL можно задавать удобные alias-поля:

- `app.source.saslMechanism`
- `app.source.username`
- `app.source.password`
- `app.source.sslCaLocation`
- `app.target.saslMechanism`
- `app.target.username`
- `app.target.password`
- `app.target.sslCaLocation`

Дополнительные Kafka-свойства можно пробросить напрямую:

- consumer: `app.source.consumer.<kafka_property>=...`
- producer: `app.target.producer.<kafka_property>=...`

Это удобно для `security.protocol`, `sasl.mechanism`, `ssl.ca.location`, таймаутов, ретраев и других стандартных свойств Kafka-клиента.

Пример конфига лежит в `config/application.properties`.
