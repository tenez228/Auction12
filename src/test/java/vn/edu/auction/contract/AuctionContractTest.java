package vn.edu.auction.contract;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Real JUnit Jupiter dynamic tests, independently named in Surefire's report. */
class AuctionContractTest {
  @TestFactory
  Stream<DynamicTest> auctionApplicationContracts() {
    return AuctionScenarios.all().stream().map(scenario ->
        DynamicTest.dynamicTest(scenario.name(), scenario.check()::run));
  }
}
