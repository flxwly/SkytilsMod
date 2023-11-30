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
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.monster.EntityZombie
import net.minecraft.util.*
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.awt.Color
import kotlin.random.Random

/**
 * Represents a timer for tracking the spawn time of the Corleone boss.
 */
object CorleoneTimer {
    private const val SECOND_IN_NS = 1000000000L
    private const val MIN_SPAWN_TIME = 60L
    private const val MAX_SPAWN_TIME = 120L
    private const val MAX_TIME = 240L

    private const val soundDelay = 400L // in Ticks

    private var lastSeen = 0L
    private var soundDelayTicks = 0L


    /**
     * The `CorleoneSpawn` class represents a spawn point for Corleone entities. It keeps track of the position, spawn time,
     * sound delay, and state of the spawn.
     *
     * @property position The position of the spawn point.
     * @property lastDeath The timestamp of the last death at the spawn point.
     * @property nextMinSpawn The timestamp indicating the earliest time at which a new entity can spawn.
     * @property nextMaxSpawn The timestamp indicating the latest time at which a new entity can spawn.
     * @property soundDelayTicks The number of ticks remaining for the sound delay.
     */
    private class CorleoneSpawn(
        var position: BlockPos,
        var lastDeath: Long = 0L,
        var nextMinSpawn: Long = 0L,
        var nextMaxSpawn: Long = 0L,
        private var soundDelayTicks: Long = 0L
    ) {

        init {
            updateSpawnTime()
        }

        private fun BlockPos.average(newPosition: BlockPos): BlockPos {
            return BlockPos(
                (x + newPosition.x) / 2,
                (y + newPosition.y) / 2,
                (z + newPosition.z) / 2
            )
        }

        /**
         * Checks if the given position is within a certain distance of the current position.
         *
         * @param newPosition The new position to be compared with the current position.
         * @return true if the distance between the current position and the new position is less than or equal to 400,
         *         false otherwise.
         */
        fun isSameSpawn(newPosition: BlockPos): Boolean {
            return position.distanceSq(newPosition) <= 400
        }

        fun updateSpawn(newPosition: BlockPos) {
            position.average(newPosition)
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
                nextMinSpawn > time -> State.DEAD
                nextMaxSpawn > time -> State.SPAWNING
                else -> State.SPAWN_OVERDUE
            }
        }

        fun shouldPlaySound(): Boolean {
            return if (getState() == State.SPAWN_OVERDUE && soundDelayTicks <= 0L) {
                soundDelayTicks = soundDelay
                true
            } else {
                soundDelayTicks--
                false
            }
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
     * Handles the death of an entity in the game.
     *
     * This method is subscribed to the LivingDeathEvent and is responsible for handling the death of an entity.
     * It checks if certain conditions are met before performing any action.
     * If the game is not in Skyblock mode, if the Corleone timer is not enabled, or if the killed entity is not
     * a "Team Treasurite", the method will return without taking any action.
     * Otherwise, it calculates the health of the entity and compares it to the expected health values.
     * If the calculated health is not within the expected values, the method will return without taking any action.
     * If the entity's spawn location is found in the list of corleoneSpawns, the spawn time for that location
     * will be updated. Otherwise, a new CorleoneSpawn object will be created and added to the list.
     *
     * @param event The LivingDeathEvent object representing the death event.
     */
    @SubscribeEvent
    fun handleEntityDeath(event: LivingDeathEvent) {
        if (!Utils.inSkyblock || !Skytils.config.corleoneTimer || event.entity.name != "Team Treasurite") return

        val entityHealth = (event.entity as EntityOtherPlayerMP).baseMaxHealth
        val expectedHealthValues = arrayOf(1_000_000.0, 2_000_000.0, 4_000_000.0, 8_000_000.0)

        if (entityHealth !in expectedHealthValues) return

        val spawn: CorleoneSpawn? = corleoneSpawns.find { it.isSameSpawn(event.entity.position) }

        if (spawn?.updateSpawnTime() != null)
            spawn.updateSpawn(event.entity.position)
        else
            corleoneSpawns.add(CorleoneSpawn(event.entity.position))
    }

    @SubscribeEvent
    fun onRenderLivingPre(event: RenderLivingEvent.Pre<EntityLivingBase?>) {
        if (!Utils.inSkyblock || !Skytils.config.corleoneTimer || event.entity.name != "Team Treasurite") return

        val entityHealth = (event.entity as EntityOtherPlayerMP).baseMaxHealth
        val expectedHealthValues = arrayOf(1_000_000.0, 2_000_000.0, 4_000_000.0, 8_000_000.0)

        if (entityHealth !in expectedHealthValues) return

        lastSeen = System.nanoTime() / SECOND_IN_NS
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
                playBossMusic()
        }

        if (lastSeen + 1 > time && soundDelayTicks <= 0) {
            soundDelayTicks = soundDelay
            playBossMusic()
        } else {
            soundDelayTicks--
        }
    }

    /**
     * Plays the boss music.
     *
     * This method adds specific sound notes to the sound queue in order to create the boss music. Each note has a corresponding
     * tick and loudness specified. The notes are added in a specific order to create the desired musical effect.
     *
     * @see SoundQueue
     * @see SoundQueue.addToQueue
     */
    private fun playBossMusic() {
        SoundQueue.addToQueue("random.orb", 1.05f, ticks=0)
        SoundQueue.addToQueue("random.orb", 1.05f, ticks=5)
        SoundQueue.addToQueue("random.orb", 1.05f, ticks=10)
        SoundQueue.addToQueue("random.orb", 0.85f, ticks=15)

        SoundQueue.addToQueue("random.orb", 0.95f, ticks=40)
        SoundQueue.addToQueue("random.orb", 0.95f, ticks=45)
        SoundQueue.addToQueue("random.orb", 0.95f, ticks=50)
        SoundQueue.addToQueue("random.orb", 0.8f, ticks=55)
    }


    /**
     * Subscribes to the RenderWorldLastEvent and renders Corleone spawns in the Crystal Hollows Skyblock island.
     *
     * @param event The RenderWorldLastEvent triggered by Minecraft.
     */
    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!Utils.inSkyblock || !Skytils.config.corleoneTimer || corleoneSpawns.isEmpty() || SBInfo.mode != SkyblockIsland.CrystalHollows.mode) return
        val matrixStack = UMatrixStack()

        GlStateManager.disableDepth()
        val time = System.nanoTime() / SECOND_IN_NS
        corleoneSpawns.forEach {
            val min = it.nextMinSpawn - time
            val max = it.nextMaxSpawn - time

            val pos = Vec3(it.position).addVector(0.5, 0.5, 0.5)

            val (label, color) = when {
                min > 0 -> "Waiting for spawn... $max" to Color.GREEN
                max > 0 -> "Corleone is spawning... $max" to Color.YELLOW
                else -> "Corleone is spawning... ${time - it.nextMaxSpawn}" to Color.RED
            }

            RenderUtil.drawLabel(pos, label, color, event.partialTicks, matrixStack)
        }

        GlStateManager.enableDepth()

    }
}