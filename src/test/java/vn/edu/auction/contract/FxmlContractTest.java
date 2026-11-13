package vn.edu.auction.contract;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural FXML contracts only: these tests do not launch or render the JavaFX toolkit. */
class FxmlContractTest {
  @ParameterizedTest(name = "FXML fields and handlers: {0}")
  @ValueSource(strings = {"login-view.fxml", "auction-list-view.fxml", "create-auction-view.fxml"})
  void fieldsAndHandlersMatchTheController(String filename) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    try (InputStream stream = getClass().getResourceAsStream("/fxml/" + filename)) {
      assertNotNull(stream, filename);
      var document = factory.newDocumentBuilder().parse(stream);
      String controllerName = document.getDocumentElement()
          .getAttributeNS("http://javafx.com/fxml/1", "controller");
      Class<?> controller = Class.forName(controllerName, false, getClass().getClassLoader());
      Set<String> fields = Arrays.stream(controller.getDeclaredFields())
          .map(Field::getName).collect(Collectors.toSet());
      Set<String> methods = Arrays.stream(controller.getDeclaredMethods())
          .map(method -> method.getName()).collect(Collectors.toSet());
      var elements = document.getElementsByTagName("*");
      for (int i = 0; i < elements.getLength(); i++) {
        Element element = (Element) elements.item(i);
        String id = element.getAttributeNS("http://javafx.com/fxml/1", "id");
        if (!id.isBlank()) { assertTrue(fields.contains(id), "Missing controller field: " + id); }
        String action = element.getAttribute("onAction");
        if (action.startsWith("#")) {
          assertTrue(methods.contains(action.substring(1)), "Missing handler: " + action);
        }
      }
    }
  }

  @Test
  void stylesheetIsPackaged() {
    assertNotNull(getClass().getResource("/css/app.css"));
  }
}
