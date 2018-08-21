## JMH Micro Benchmarks

The way to run the benchmarks is as follows:

```bash
# from this directory 
../gradlew jmh
```

Currently in gradle composite builds this will always use remote artifacts,
and not attempt to use project local artifacts. To use project local
artifacts you can manually include the project on the command line:

```bash
../gradlew jmh --include-build=../servicetalk-concurrent-api
```

### Potential Issues

If you abruptly stop a benchmark run (e.g. CTRL+C) the benchmarks may fail
to run again. There are two potential remedies for this.

1. Kill / restart the gradle daemon.
2. `rm -rf ../.gradle`
