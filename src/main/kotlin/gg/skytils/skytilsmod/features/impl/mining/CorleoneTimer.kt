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

import gg.essential.universal.UMatrixStack
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.core.SoundQueue
import gg.skytils.skytilsmod.core.structure.GuiElement
import gg.skytils.skytilsmod.features.impl.handlers.MayorInfo
import gg.skytils.skytilsmod.utils.*
import gg.skytils.skytilsmod.utils.graphics.SmartFontRenderer
import gg.skytils.skytilsmod.utils.graphics.colors.CommonColors
import gg.skytils.skytilsmod.utils.graphics.colors.CustomColor
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.monster.EntityZombie
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3i
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.awt.Color

/**
 * Represents a timer for tracking the spawn time of the Corleone boss.
 */
object CorleoneTimer {
    private const val SECOND_IN_NS = 1000000000L
    private const val MIN_SPAWN_TIME = 60L
    private const val MAX_SPAWN_TIME = 120L
    private const val MAX_TIME = 240L

    private const val soundDelay = 100L // in Ticks


    private class CorleoneSpawn(var pos: BlockPos) {
        var lastDeath = 0L
        var nextMinSpawn = 0L
        var nextMaxSpawn = 0L
        var soundDelayTicks = 0L

        init {
            updateSpawnTime()
        }

        fun isSameSpawnAndUpdate(newPos: BlockPos): Boolean {
            if (pos.distanceSq(newPos) <= 400) {
                pos = BlockPos(
                    (pos.x + newPos.x) / 2,
                    (pos.y + newPos.y) / 2,
                    (pos.z + newPos.z) / 2
                )
                return true
            }
            return false
        }

        fun updateSpawnTime() {
            lastDeath = System.nanoTime() / SECOND_IN_NS
            nextMinSpawn = lastDeath + MIN_SPAWN_TIME
            nextMaxSpawn = lastDeath + MAX_SPAWN_TIME
        }

        enum class State {
            DEAD,
            SPAWNING,
            SPAWN_OVERDUE
        }

        fun getState(): State {
            val time = System.nanoTime() / SECOND_IN_NS
            return when {
                nextMinSpawn - time > 0 -> State.DEAD
                nextMaxSpawn - time > 0 -> State.SPAWNING
                else -> State.SPAWN_OVERDUE
            }
        }

        fun shouldPlaySound(): Boolean {
            if (getState() == State.SPAWN_OVERDUE) {
                if (soundDelayTicks == 0L) {
                    soundDelayTicks = soundDelay
                    return true
                }
                soundDelayTicks--
            }
            return false
        }
    }


    private var corleoneSpawns = arrayListOf<CorleoneSpawn>()

    init {
        reset()
    }

    private fun reset() {
        corleoneSpawns.clear()
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Load) {
        reset()
    }

    /**
     * Method to handle entity death events.
     *
     * This method is subscribed to the LivingDeathEvent. It checks if the event is triggered in the
     * Skyblock and if the Corleone timer is enabled in the Skytils configuration. It further checks if
     * the entity that died is a "Team Treasurite". If any of these conditions are not met, the method
     * returns and does nothing.
     *
     * If the entity that died is a "Team Treasurite", it retrieves its maximum health and compares it
     * with the expected health based on the mayor perks. If the health values do not match, the method
     * returns and does nothing.
     *
     * If both the entity and the health values match the expected values, it updates the spawn time
     * and sets the isCorleoneFound flag to true.
     *
     * @param event The LivingDeathEvent triggered when an entity dies.
     */
    @SubscribeEvent
    fun handleEntityDeath(event: LivingDeathEvent) {
        if (!Utils.inSkyblock || !Skytils.config.corleoneTimer || event.entity.name != "Team Treasurite") return

        val entityHealth = (event.entity as EntityOtherPlayerMP).baseMaxHealth
        val expectedHealth = if (MayorInfo.mayorPerks.contains("DOUBLE MOBS HP!!!")) 2_000_000.0 else 1_000_000.0

        if (entityHealth != expectedHealth) return

        if (event.entity !is EntityZombie) return

        if (corleoneSpawns.find { it.isSameSpawnAndUpdate(event.entity.position) }?.updateSpawnTime() == null)
            corleoneSpawns.add(CorleoneSpawn(event.entity.position))
    }


    /**
     * This method is a listener for the ClientTickEvent event. It is triggered every tick in the game.
     * It checks if various conditions are met and plays a sound effect if those conditions are true.
     *
     * @param event The ClientTickEvent that triggered this method
     */
    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (!Utils.inSkyblock || !Skytils.config.corleoneTimer || corleoneSpawns.isEmpty() || SBInfo.mode != SkyblockIsland.CrystalHollows.mode) return


        val time = System.nanoTime() / SECOND_IN_NS
        corleoneSpawns.removeIf { it.lastDeath + MAX_TIME < time }

        for (spawn in corleoneSpawns) {
            if (spawn.shouldPlaySound())
                SoundQueue.addToQueue("random.orb", 0.5f, isLoud = true)
        }
    }

    /**
     * Represents a GUI element for displaying the Corleone spawn timer.
     *
     * @property toggled Indicates whether the Corleone timer is toggled on or off.
     * @property height The height of the GUI element.
     * @property width The width of the GUI element.
     *
     * @constructor Creates a CorleoneTimerGuiElement instance and registers it with the GUI manager.
     */

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!Utils.inSkyblock || !Skytils.config.corleoneTimer || corleoneSpawns.isEmpty() || SBInfo.mode != SkyblockIsland.CrystalHollows.mode) return
        val matrixStack = UMatrixStack()

        GlStateManager.disableDepth()
        val time = System.nanoTime() / SECOND_IN_NS
        for (spawn in corleoneSpawns) {
            val label = when (spawn.getState()) {
                CorleoneSpawn.State.SPAWNING -> stringWithFormat("Waiting for spawn... ", spawn.nextMinSpawn - time)
                CorleoneSpawn.State.DEAD -> stringWithFormat("Corleone is spawning... ", spawn.nextMaxSpawn - time)
                else -> stringWithFormat("Corleone is spawning... ", time - spawn.nextMaxSpawn)
            }
            RenderUtil.renderWaypointText(label, spawn.pos, event.partialTicks, matrixStack)
        }

        GlStateManager.enableDepth()

    }

    private fun stringWithFormat(label: String, time: Long): String {
        return "$label (${time / 60}:${"%02d".format(time % 60)})"
    }
}