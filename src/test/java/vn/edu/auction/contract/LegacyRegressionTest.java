package vn.edu.auction.contract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.auction.domain.auction.Auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs each legacy main in its own JVM, so Singleton state cannot leak between test suites. */
class LegacyRegressionTest {
  @TempDir Path output;

  @ParameterizedTest(name = "Legacy regression: {0}")
  @ValueSource(strings = {
      "vn.edu.auction.domain.user.UserModelSelfTest",
      "vn.edu.auction.domain.DomainModelSelfTest",
      "vn.edu.auction.application.AuctionManagerSelfTest",
      "vn.edu.auction.application.AuctionServiceSelfTest"})
  @Timeout(45)
  void oldContractsStillHold(String className) throws Exception {
    String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    String main = Path.of(Auction.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    String tests = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    Path log = output.resolve(className + ".log");
    Process process = new ProcessBuilder(javaExecutable, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8", "-cp", main + java.io.File.pathSeparator + tests, className)
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      assertTrue(process.waitFor(Duration.ofSeconds(35).toMillis(), TimeUnit.MILLISECONDS),
          "Timed out: " + className);
      assertEquals(0, process.exitValue(), () -> {
        try { return Files.readString(log); }
        catch (java.io.IOException ex) { return "Cannot read process log: " + ex; }
      });
    } finally {
      if (process.isAlive()) { process.destroyForcibly(); }
    }
  }
}
