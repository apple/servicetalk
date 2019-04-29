# Using the docker images

```
cd /path/to/servicetalk/
```

## centos 7 with java 8

```
docker-compose -f docker/docker-compose.yaml -f docker/docker-compose.centos7.java8.yaml run test
```

## centos 7 with java 11

```
docker-compose -f docker/docker-compose.yaml -f docker/docker-compose.centos7.java11.yaml run test
```
