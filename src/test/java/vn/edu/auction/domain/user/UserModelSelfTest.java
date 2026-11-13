package vn.edu.auction.domain.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/** Standalone checks: no external test library and no -ea flag required. */
public final class UserModelSelfTest {
  private static int passed;

  private UserModelSelfTest() {
  }

  public static void main(String[] args) {
    List<BiFunction<String, String, User>> factories = List.of(
        Bidder::new, Seller::new, Admin::new);
    List<UserRole> roles = List.of(UserRole.BIDDER, UserRole.SELLER, UserRole.ADMIN);

    for (int i = 0; i < factories.size(); i++) {
      BiFunction<String, String, User> factory = factories.get(i);
      UserRole role = roles.get(i);
      run(role + ": construction and text normalization", () -> {
        User user = factory.apply("  Demo.User  ", "  Nguyen An  ");
        check(user.getId() != null, "id must exist");
        check(user.getUsername().equals("Demo.User"), "username must preserve case");
        check(user.getDisplayName().equals("Nguyen An"), "displayName must be stripped");
        check(user.getRole() == role, "incorrect role");
      });
      run(role + ": null username", () -> expectThrows(
          IllegalArgumentException.class, () -> factory.apply(null, "Valid Name")));
      run(role + ": empty username", () -> expectThrows(
          IllegalArgumentException.class, () -> factory.apply("", "Valid Name")));
      run(role + ": blank username", () -> expectThrows(
          IllegalArgumentException.class, () -> factory.apply(" \t\n ", "Valid Name")));
      run(role + ": null displayName", () -> expectThrows(
          IllegalArgumentException.class, () -> factory.apply("demo", null)));
      run(role + ": empty displayName", () -> expectThrows(
          IllegalArgumentException.class, () -> factory.apply("demo", "")));
      run(role + ": blank displayName", () -> expectThrows(
          IllegalArgumentException.class, () -> factory.apply("demo", " \t\n ")));
      run(role + ": rename preserves id, username, role and hashCode", () -> {
        User user = factory.apply("demo", "Old Name");
        UUID id = user.getId();
        int hash = user.hashCode();
        user.changeDisplayName("  New Name  ");
        check(user.getDisplayName().equals("New Name"), "rename failed");
        check(user.getId().equals(id), "id changed");
        check(user.getUsername().equals("demo"), "username changed");
        check(user.getRole() == role, "role changed");
        check(user.hashCode() == hash, "hashCode changed");
      });
      run(role + ": invalid rename leaves state unchanged", () -> {
        User user = factory.apply("demo", "Old Name");
        expectThrows(IllegalArgumentException.class, () -> user.changeDisplayName(null));
        expectThrows(IllegalArgumentException.class, () -> user.changeDisplayName(""));
        expectThrows(IllegalArgumentException.class, () -> user.changeDisplayName("  "));
        check(user.getDisplayName().equals("Old Name"), "invalid input changed state");
      });
    }

    run("Explicit ids are retained for all roles", () -> {
      UUID id = UUID.randomUUID();
      check(new Bidder(id, "bidder", "Bidder").getId().equals(id), "Bidder id changed");
      check(new Seller(id, "seller", "Seller").getId().equals(id), "Seller id changed");
      check(new Admin(id, "admin", "Admin").getId().equals(id), "Admin id changed");
    });
    run("Null ids are rejected for all roles", () -> {
      expectThrows(NullPointerException.class, () -> new Bidder(null, "bidder", "Bidder"));
      expectThrows(NullPointerException.class, () -> new Seller(null, "seller", "Seller"));
      expectThrows(NullPointerException.class, () -> new Admin(null, "admin", "Admin"));
    });
    run("Fresh users receive different ids", () -> {
      User first = new Bidder("same", "Same");
      User second = new Bidder("same", "Same");
      check(!first.getId().equals(second.getId()), "generated ids unexpectedly equal");
      check(!first.equals(second), "different ids must not compare equal");
    });
    run("Equality is reflexive, symmetric and transitive", () -> {
      UUID id = UUID.randomUUID();
      User a = new Bidder(id, "a", "A");
      User b = new Bidder(id, "b", "B");
      User c = new Bidder(id, "c", "C");
      check(a.equals(a), "not reflexive");
      check(a.equals(b) && b.equals(a), "not symmetric");
      check(a.equals(b) && b.equals(c) && a.equals(c), "not transitive");
      check(a.hashCode() == b.hashCode(), "equal objects need equal hash codes");
    });
    run("Different concrete classes are not equal", () -> {
      UUID id = UUID.randomUUID();
      User bidder = new Bidder(id, "demo", "Demo");
      User seller = new Seller(id, "demo", "Demo");
      check(!bidder.equals(seller) && !seller.equals(bidder), "roles compared equal");
    });
    run("Null and non-Entity comparisons are false", () -> {
      User user = new Admin("admin", "Admin");
      check(!user.equals(null), "user equals null");
      check(!user.equals("admin"), "user equals a String");
    });
    run("HashSet membership survives a displayName change", () -> {
      User user = new Seller("seller", "Old Name");
      Set<User> users = new HashSet<>();
      users.add(user);
      user.changeDisplayName("New Name");
      check(users.contains(user), "membership lost after rename");
    });
    run("Polymorphic role dispatch", () -> {
      List<User> users = List.of(new Bidder("b", "B"), new Seller("s", "S"),
          new Admin("a", "A"));
      for (int i = 0; i < users.size(); i++) {
        check(users.get(i).getRole() == roles.get(i), "polymorphic dispatch failed");
      }
    });
    run("Role labels are present", () -> {
      for (UserRole role : UserRole.values()) {
        check(!role.getLabel().isBlank(), "missing label");
      }
    });
    run("toString includes the type and role", () -> {
      String value = new Bidder("demo", "Demo").toString();
      check(value.startsWith("Bidder{"), "missing type");
      check(value.contains("username='demo'"), "missing username");
      check(value.contains("role=BIDDER"), "missing role");
    });
    System.out.println("PASS: " + passed + " checks.");
  }

  private static void run(String name, Runnable action) {
    try {
      action.run();
      passed++;
      System.out.println("PASS - " + name);
    } catch (RuntimeException | AssertionError error) {
      throw new AssertionError("FAIL - " + name, error);
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void expectThrows(Class<? extends RuntimeException> expected, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException error) {
      if (expected.isInstance(error)) {
        return;
      }
      throw new AssertionError("Wrong exception: " + error.getClass().getName(), error);
    }
    throw new AssertionError("Expected exception: " + expected.getName());
  }
}
