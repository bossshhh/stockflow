package com.stockflow.user;

/** Must match ck_users_role (V9). Spring Security sees these as ROLE_CUSTOMER etc. */
public enum Role {
    /** Places orders; sees and cancels only their own. */
    CUSTOMER,
    /** Fulfils orders: packs, ships and marks them delivered; reads stock. Nothing else. */
    WAREHOUSE,
    /** Runs the warehouse: products, stock, confirming orders, reports. */
    STAFF,
    /** Everything STAFF can do, plus managing staff accounts. */
    ADMIN
}
