package com.thin.downloadmanager

/**
 * Created by maniselvaraj on 15/4/15.
 */
class RetryError : Exception {

    constructor() : super("Maximum retry exceeded") {}

    constructor(cause: Throwable) : super(cause) {}
}
