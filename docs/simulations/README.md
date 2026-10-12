# Simulation scripts (not part of the build)

`Sim5_studentized_residuals.java` is the script behind the studentized-residual calibration quoted in the CHANGELOG. It is not part of the build (`build.sh` and `pom.xml` only compile `src/`). Build the project first, then:

```
sh build.sh
mkdir -p /tmp/sim && javac -cp build/classes -d /tmp/sim docs/simulations/Sim5_studentized_residuals.java
java -Dmetajava.threads=1 -cp build/classes:/tmp/sim org.metajava.Sim5 1 1500   # random-effects model
java -Dmetajava.threads=1 -cp build/classes:/tmp/sim org.metajava.Sim5 2 1500   # meta-regression
```

The results are empirical rates for the simulated scenarios only, not general performance guarantees; read the CHANGELOG for how to interpret them. The scripts for the earlier GOSH cluster simulations are not included.
