# Sprintly CLI

A terminal-based client for Sprintly, built with Java, Picocli, and Lanterna.

## Features

- **Authentication**: Register, Login, Logout, and Token Refresh.
- **Task Management**: Create, List, and View detailed Task information.
- **Token Persistence**: Securely stores tokens locally in `~/.sprintly-cli.json`.

## Commands

### Authentication

- `sprintly register -e <email> -p <password> -n <name>`
- `sprintly login -e <email> -p <password>`
- `sprintly logout`
- `sprintly refresh`

### Task Management

- `sprintly task list`
- `sprintly task create -t "Title" -d "Description" [-a <assigneeId>]`
- `sprintly task get <taskId>`

## Building

From the root directory, run:
```bash
mvn clean install -pl sprintly-cli -am
```

The executable JAR will be generated in `sprintly-cli/target/sprintly-cli-1.0.0-SNAPSHOT.jar`.

## Running

```bash
java -jar sprintly-cli/target/sprintly-cli-1.0.0-SNAPSHOT.jar [command]
```
