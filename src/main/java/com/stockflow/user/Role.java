package com.stockflow.user;

/** Must match ck_users_role in V5__create_users.sql. Spring Security sees these as ROLE_CUSTOMER etc. */
public enum Role {
    /** Places orders; sees and cancels only their own. */
    CUSTOMER,
    /** Runs the warehouse: products, stock, confirming orders, reports. */
    STAFF,
    /** Everything STAFF can do, plus managing staff accounts. */
    ADMIN
}
