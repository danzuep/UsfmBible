package io.github.danzuep.usfmbible.tests

import io.github.danzuep.usfmbible.core.provider.TimeProvider

class TestTimeProvider(
    override var currentTimeMillis: Long = System.currentTimeMillis(),
    override var elapsedRealtime: Long = 0L
) : TimeProvider
