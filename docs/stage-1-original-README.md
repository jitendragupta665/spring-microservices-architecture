# Microservices Architecture

A production-ready Spring Boot microservices architecture with inter-service communication. This project demonstrates a scalable, containerized microservices setup with two independent services: Order Service and User Service.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│           Docker Network (micro-net)                    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────┐   ┌──────────────────────┐  │
│  │   Order Service      │   │   User Service       │  │
│  │   (Port: 8081)       │ ─→│   (Port: 8080)       │  │
│  │                      │   │                      │  │
│  │ GET /order           │   │ GET /user            │  │
│  └──────────────────────┘   └──────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Services

### 1. User Service
- **Port:** 8080
- **Technology:** Spring Boot 4.0.2, Java 21
- **Endpoints:**
  - `GET /user` - Returns user information (Rahul, age 22)
- **Purpose:** Provides user data for other services

### 2. Order Service
- **Port:** 8081
- **Technology:** Spring Boot 4.0.2, Java 21
- **Endpoints:**
  - `GET /order` - Creates an order and fetches user information from User Service
- **Purpose:** Order management and inter-service orchestration

## Prerequisites

- **Java 21+**
- **Maven 3.6+**
- **Docker** and **Docker Compose** (for containerized deployment)
- **Git**

### Installation
```bash
# Verify Java version
java -version

# Verify Maven version
mvn -version

# Verify Docker
docker --version
```

## Project Structure

```
microservices/
├── order-service/              # Order Service microservice
│   ├── src/
│   │   ├── main/java/com/example/order_service/
│   │   │   ├── OrderServiceApplication.java
│   │   │   ├── client/
│   │   │   │   └── UserClient.java          # REST client for User Service
│   │   │   ├── controller/
│   │   │   │   └── OrderController.java     # REST endpoints
│   │   │   └── service/
│   │   │       └── OrderService.java        # Business logic
│   │   ├── main/resources/
│   │   │   └── application.properties
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
│
├── user-service/               # User Service microservice
│   ├── src/
│   │   ├── main/java/com/example/user_service/
│   │   │   ├── UserServiceApplication.java
│   │   │   ├── controller/
│   │   │   │   └── UserController.java      # REST endpoints
│   │   │   ├── model/
│   │   │   │   └── User.java                # Data model
│   │   │   └── service/
│   │   │       └── UserService.java         # Business logic
│   │   ├── main/resources/
│   │   │   └── application.properties
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
│
├── docker-compose.yml          # Container orchestration
└── README.md                   # This file
```

## Getting Started

### Development Mode (Without Docker)

#### 1. Clone and Navigate
```bash
git clone <your-repo-url>
cd microservices
```

#### 2. Build Both Services
```bash
# Build User Service
cd user-service
mvn clean install

# Build Order Service
cd ../order-service
mvn clean install
```

#### 3. Run Services (in separate terminals)

**Terminal 1 - User Service:**
```bash
cd user-service
mvn spring-boot:run
```
Expected output:
```
Tomcat initialized with port 8080 (http)
```

**Terminal 2 - Order Service:**
```bash
cd order-service
mvn spring-boot:run
```
Expected output:
```
Tomcat initialized with port 8081 (http)
```

#### 4. Test the Services

**User Service:**
```bash
curl http://localhost:8080/user
# Response: {"name":"Rahul","age":22}
```

**Order Service:**
```bash
curl http://localhost:8081/order
# Response: Order created for -> {"name":"Rahul","age":22}
```

### Production Mode (With Docker & Docker Compose)

#### 1. Build Docker Images

Build individual services:
```bash
# Build User Service image
cd user-service
docker build -t user-service:1.0 .

# Build Order Service image
cd ../order-service
docker build -t order-service:1.0 .
```

Or build all at once using Docker Compose:
```bash
docker-compose build
```

#### 2. Create Docker Network
```bash
docker network create micro-net
```

#### 3. Run with Docker Compose
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

Or run manually:
```bash
# Terminal 1 - User Service
docker run -d \
  --name user-service \
  --network micro-net \
  -p 8080:8080 \
  user-service:1.0

# Terminal 2 - Order Service
docker run -d \
  --name order-service \
  --network micro-net \
  -p 8081:8081 \
  order-service:1.0
```

#### 4. Test Docker Containers
```bash
# Test User Service
curl http://localhost:8080/user

# Test Order Service
curl http://localhost:8081/order
```

#### 5. Cleanup
```bash
# Stop and remove containers
docker-compose down

# Or manually
docker stop user-service order-service
docker rm user-service order-service
docker network rm micro-net
```

## Configuration

### User Service - `user-service/src/main/resources/application.properties`
```properties
server.port=8080
spring.application.name=user-service
```

### Order Service - `order-service/src/main/resources/application.properties`
```properties
server.port=8081
spring.application.name=order-service
```

### Environment-Specific Configurations
Create additional properties files:
- `application-dev.properties` - Development settings
- `application-prod.properties` - Production settings

Run with specific profile:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

## Building & Testing

### Build
```bash
mvn clean package
```

### Run Tests
```bash
mvn test
```

### Run Tests for Specific Service
```bash
cd user-service
mvn test

cd ../order-service
mvn test
```

### Code Quality & Linting
```bash
# Using Maven plugins (if configured)
mvn checkstyle:check
mvn spotbugs:check
```

## Deployment

### Local Development Deployment
1. Run `mvn clean install` in each service directory
2. Use `mvn spring-boot:run` to start services
3. Services communicate via `localhost:port`

### Docker Container Deployment
1. Build images using `docker build`
2. Push to Docker registry (Docker Hub, AWS ECR, etc.)
3. Deploy using Docker Compose or Kubernetes

### Kubernetes Deployment (Optional)
Create deployment manifests:
- Service definitions
- Deployment specs
- ConfigMap for configurations
- Service discovery using DNS

## API Documentation

### User Service

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| GET | `/user` | Get user information | `{"name":"Rahul","age":22}` |

### Order Service

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| GET | `/order` | Create order and fetch user info | `"Order created for -> {"name":"Rahul","age":22}"` |

## Troubleshooting

### Issue: Port Already in Use
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -i :8080
kill -9 <PID>
```

### Issue: Docker Network Not Found
```bash
# Create network if missing
docker network create micro-net

# Verify network exists
docker network ls
```

### Issue: Service Connection Error
Ensure:
- Both containers are running: `docker ps`
- Both are on the same network: `docker network inspect micro-net`
- Correct service names are used in connections
- Firewall rules allow inter-container communication

### Issue: Maven Build Fails
```bash
# Clear Maven cache
mvn clean

# Rebuild
mvn install -U

# Force dependency update
mvn dependency:purge-local-repository
```

## Dependencies

### Core Dependencies
- **Spring Boot Starter WebMVC** - Web framework
- **Spring Boot DevTools** - Development reload support
- **Lombok** - Boilerplate code reduction

### Testing
- **Spring Boot Test Starter** - Testing framework

## Best Practices

1. **Containerization:** Always containerize services for consistency
2. **Network Isolation:** Use named networks for inter-service communication
3. **Service Discovery:** Use DNS naming for container communication
4. **Health Checks:** Implement health check endpoints
5. **Logging:** Centralize logs using ELK, Splunk, or cloud solutions
6. **Monitoring:** Use Prometheus and Grafana for metrics
7. **Configuration Management:** Use ConfigMap or environment variables
8. **Error Handling:** Implement proper exception handling and error responses
9. **API Versioning:** Include version in endpoints (e.g., `/api/v1/user`)
10. **Documentation:** Keep API documentation updated with Swagger/OpenAPI

## Monitoring & Logging

### Enable Debug Logging
Add to `application.properties`:
```properties
logging.level.root=INFO
logging.level.com.example=DEBUG
logging.level.web=DEBUG
```

### Spring Actuator (Optional)
Add dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Endpoints:
- `/actuator/health` - Health status
- `/actuator/metrics` - Application metrics
- `/actuator/env` - Environment details

## Performance Optimization

1. **Connection Pooling:** Configure database connection pools
2. **Caching:** Implement Redis for distributed caching
3. **Load Balancing:** Use Nginx or HAProxy
4. **Rate Limiting:** Implement rate limiting for APIs
5. **Async Processing:** Use async/reactive patterns for I/O operations

## Security

- Implement OAuth 2.0 / JWT for authentication
- Use HTTPS for all external communication
- Enable CORS only for trusted origins
- Sanitize inputs to prevent injection attacks
- Implement rate limiting to prevent DDoS
- Use secrets management (Vault, AWS Secrets Manager)

## Contributing

1. Create a feature branch: `git checkout -b feature/feature-name`
2. Commit changes: `git commit -m "Add feature description"`
3. Push to branch: `git push origin feature/feature-name`
4. Create a Pull Request

## License

This project is licensed under the MIT License - see LICENSE file for details.

## Support

For issues and questions:
- Create an issue in the repository
- Contact the development team
- Check existing documentation

## Changelog

### Version 1.0.0 (Initial Release)
- User Service basic implementation
- Order Service basic implementation
- Docker support
- Basic inter-service communication

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Maven Documentation](https://maven.apache.org/guides/)
- [Java 21 Features](https://openjdk.org/projects/jdk/21/)
