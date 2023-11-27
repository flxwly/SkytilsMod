/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2020-2023 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package gg.skytils.skytilsmod.features.impl.mining

import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.core.SoundQueue
import gg.skytils.skytilsmod.core.structure.GuiElement
import gg.skytils.skytilsmod.features.impl.handlers.MayorInfo
import gg.skytils.skytilsmod.utils.SBInfo
import gg.skytils.skytilsmod.utils.SkyblockIsland
import gg.skytils.skytilsmod.utils.Utils
import gg.skytils.skytilsmod.utils.baseMaxHealth
import gg.skytils.skytilsmod.utils.graphics.SmartFontRenderer
import gg.skytils.skytilsmod.utils.graphics.colors.CommonColors
import io.ktor.server.sessions.*
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.monster.EntityZombie
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.living.LivingSpawnEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent

object CorleoneTimer {
    var soundDelayTicks = 0
    const val second = 1000000000L // one s in ns to convert nanoTime()

    const val minSpawnTime = 60 // in s
    const val maxSpawnTime = 120 // in s
    const val maxTime = 240 // in s

    var lastDeath = 0L
    var nextMinSpawn = 0L
    var nextMaxSpawn = 0L
    var foundCorleone = false

    init {
        CorleoneTimerGuiElement()
        reset()
    }

    private fun reset() {
        lastDeath = -1L
        nextMinSpawn = -1L
        nextMaxSpawn = -1L
        foundCorleone = false
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Load) {
        reset()
    }

    @SubscribeEvent()
    fun onEntityDeath(event: LivingDeathEvent) {
        println(event.entity.name)
        if (!Utils.inSkyblock) return
        if (Skytils.config.corleoneTimer
            && event.entity.name == "Team Treasurite"
            && (event.entity as EntityOtherPlayerMP).baseMaxHealth == if (MayorInfo.mayorPerks.contains("DOUBLE MOBS HP!!!")) 2_000_000.0 else 1_000_000.0
        ) {
            lastDeath = System.nanoTime() / second
            nextMinSpawn = lastDeath + minSpawnTime
            nextMaxSpawn = lastDeath + maxSpawnTime
            foundCorleone = true
        }
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (Utils.inSkyblock
            && Skytils.config.corleoneTimer
            && SBInfo.mode == SkyblockIsland.CrystalHollows.mode
            && foundCorleone
            && (System.nanoTime() / second) - nextMaxSpawn >= 0
        ) {
            if (soundDelayTicks <= 0) {
                soundDelayTicks = 20 * 5
                SoundQueue.addToQueue("random.orb", 0.5f, isLoud = true)
            } else {
                soundDelayTicks--
            }
        }
    }

    class CorleoneTimerGuiElement : GuiElement(name = "Corleone Spawn Timer", x = 10, y = 10) {
        override fun render() {
            if (Utils.inSkyblock
                && SBInfo.mode == SkyblockIsland.CrystalHollows.mode
                && toggled && foundCorleone) {
                val time = System.nanoTime() / second

                val min = nextMinSpawn - time
                val max = nextMaxSpawn - time

                if (min > 0) {
                    fr.drawString(
                        "Waiting for spawn... (${min / 60}:" +
                                "${"%02d".format(min % 60)})",
                        0f,
                        0f,
                        CommonColors.BLUE,
                        SmartFontRenderer.TextAlignment.LEFT_RIGHT,
                        SmartFontRenderer.TextShadow.NONE
                    )
                } else if (max > 0) {
                    fr.drawString(
                        "Corleone is spawning... (${(max) / 60}:" +
                                "${"%02d".format((max) % 60)})",
                        0f,
                        0f,
                        CommonColors.YELLOW,
                        SmartFontRenderer.TextAlignment.LEFT_RIGHT,
                        SmartFontRenderer.TextShadow.NONE
                    )
                } else if (lastDeath + maxTime > time) {
                    fr.drawString(
                        "Corleone is spawning... (${(time - nextMaxSpawn) / 60}:" +
                                "${"%02d".format((time - nextMaxSpawn) % 60)})",
                        0f,
                        0f,
                        CommonColors.RED,
                        SmartFontRenderer.TextAlignment.LEFT_RIGHT,
                        SmartFontRenderer.TextShadow.NONE
                    )
                } else {
                    reset()
                }
            }
        }

        override fun demoRender() {
            fr.drawString(
                "99:99",
                0f,
                0f,
                CommonColors.ORANGE,
                SmartFontRenderer.TextAlignment.LEFT_RIGHT,
                SmartFontRenderer.TextShadow.NONE
            )
        }

        override val toggled: Boolean
            get() = Skytils.config.corleoneTimer
        override val height: Int
            get() = fr.FONT_HEIGHT
        override val width: Int
            get() = fr.getStringWidth("Corleone is spawning... (99:99)")

        init {
            Skytils.guiManager.registerElement(this)
        }

    }
}