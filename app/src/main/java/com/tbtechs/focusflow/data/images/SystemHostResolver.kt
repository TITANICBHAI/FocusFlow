package com.tbtechs.focusflow.data.images

import java.net.InetAddress

/** Resolves a host name with the platform resolver. */
class SystemHostResolver : HostResolver {
    override fun resolve(host: String): List<InetAddress> =
        InetAddress.getAllByName(host).toList()
}
