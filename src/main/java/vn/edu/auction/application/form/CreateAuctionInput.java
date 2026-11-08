package vn.edu.auction.application.form;

import vn.edu.auction.domain.item.ItemType;

/** Raw form data: no JavaFX controls and no caller-supplied seller identity. */
public record CreateAuctionInput(
    ItemType type, String name, String description, String detailOne, String detailTwo,
    String numberOne, String numberTwo, String price, String delay, String duration) {
}
