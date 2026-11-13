package vn.edu.auction.contract;

/** Executes the shared contracts without dependencies. This is NOT the JUnit engine. */
public final class AuctionCoreSelfTest {
  private AuctionCoreSelfTest() {
  }

  public static void main(String[] args) throws Exception {
    int passed = 0;
    int failed = 0;
    for (AuctionScenarios.Scenario scenario : AuctionScenarios.all()) {
      try {
        scenario.check().run();
        passed++;
        System.out.println("PASS - " + scenario.name());
      } catch (Exception | AssertionError ex) {
        failed++;
        System.err.println("FAIL - " + scenario.name());
        ex.printStackTrace();
      }
    }
    System.out.println("RESULT: " + passed + " passed, " + failed + " failed (offline contracts, NOT JUnit).");
    if (failed > 0) { throw new AssertionError("Auction contracts failed"); }
  }
}
