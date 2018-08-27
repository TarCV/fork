package com.shazam.fork.pooling

import com.shazam.fork.model.Device
import com.shazam.fork.model.DisplayGeometry
import com.shazam.fork.model.Pool
import com.shazam.fork.runner.functionalTestTestcaseDuration

class StubPoolLoader() : PoolLoader {
    override fun loadPools(): Collection<Pool> {
        synchronized(StubPoolLoader.Companion) {
            if (pools == null) {
                val device1 = createStubDevice("fork-5554", 25)
                val device2 = createStubDevice("fork-5556", 25)

                val pool = Pool.Builder()
                        .withName("Stub 2 device pool")
                        .addDevice(device1)
                        .addDevice(device2)
                        .build()
                pools = listOf(pool)
            }
            return pools!!
        }
    }

    companion object {
        var pools: Collection<Pool>? = null
    }
}

private fun createStubDevice(serial: String, api: Int): Device {
    val manufacturer = "fork"
    val model = "Emu-$api"
    val stubDevice = StubDevice(serial, manufacturer, model, serial, api, "",
            functionalTestTestcaseDuration)
    return Device.Builder()
            .withApiLevel(api.toString())
            .withDisplayGeometry(DisplayGeometry(640))
            .withManufacturer(manufacturer)
            .withModel(model)
            .withSerial(serial)
            .withDeviceInterface(stubDevice)
            .build()
}
