package authtemplate

/**
 * Gets a key from a [[Map]], but compares while ignoring case.
 */
def getCaseInsensitive[V](m: Map[String, V], key: String): Option[V] = {
  m.find { (k, _) =>
    k.equalsIgnoreCase(key)
  }.map { (_, v) =>
    v
  }
}

