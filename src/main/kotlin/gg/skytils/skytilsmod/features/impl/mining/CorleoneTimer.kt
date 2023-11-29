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
import gg.skytils.skytilsmod.utils.graphics.colors.CustomColor
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent

/**
 * Represents a timer for tracking the spawn time of the Corleone boss.
 */
object CorleoneTimer {
    private var soundDelayTicks = 0
    private const val SECOND_IN_NS = 1000000000L
    private const val MIN_SPAWN_TIME = 60L
    private const val MAX_SPAWN_TIME = 120L
    private const val MAX_TIME = 240L

    private var lastDeath = 0L
    private var nextMinSpawn = 0L
    private var nextMaxSpawn = 0L
    private var isCorleoneFound = false

    init {
        CorleoneTimerGuiElement()
        reset()
    }

    private fun reset() {
        lastDeath = -1L
        nextMinSpawn = -1L
        nextMaxSpawn = -1L
        isCorleoneFound = false
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

        updateSpawnTime()
        isCorleoneFound = true
    }

    /**
     * Updates the spawn time for the next Corleone boss.
     * The spawn time is calculated based on the current time and the minimum and maximum spawn time values.
     *
     * This method sets the `lastDeath` variable to the current time in seconds.
     * It then calculates the `nextMinSpawn` and `nextMaxSpawn` variables by adding the minimum and maximum spawn time values to the `lastDeath` variable.
     *
     * This method does not return any value.
     */
    private fun updateSpawnTime() {
        lastDeath = System.nanoTime() / SECOND_IN_NS
        nextMinSpawn = lastDeath + MIN_SPAWN_TIME
        nextMaxSpawn = lastDeath + MAX_SPAWN_TIME
    }


    /**
     * This method is a listener for the ClientTickEvent event. It is triggered every tick in the game.
     * It checks if various conditions are met and plays a sound effect if those conditions are true.
     *
     * @param event The ClientTickEvent that triggered this method
     */
    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (!Utils.inSkyblock || !Skytils.config.corleoneTimer || !isCorleoneFound
            || SBInfo.mode != SkyblockIsland.CrystalHollows.mode ||
            System.nanoTime() / SECOND_IN_NS - nextMaxSpawn < 0
        ) return

        if (soundDelayTicks <= 0) {
            soundDelayTicks = 20 * 5
            SoundQueue.addToQueue("random.orb", 0.5f, isLoud = true)
        } else {
            soundDelayTicks--
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
    class CorleoneTimerGuiElement : GuiElement(name = "Corleone Spawn Timer", x = 10, y = 10) {

        /**
         * This method is responsible for rendering Corleone spawn information in the game.
         * It checks the necessary conditions for rendering and displays relevant messages accordingly.
         * This method should be called whenever a refresh or update is required to display accurate information.
         */
        override fun render() {
            if (!Utils.inSkyblock || !toggled || !isCorleoneFound || SBInfo.mode != SkyblockIsland.CrystalHollows.mode) return

            val time = System.nanoTime() / SECOND_IN_NS
            val min = nextMinSpawn - time
            val max = nextMaxSpawn - time

            when {
                min > 0 -> drawStringWithFormat("Waiting for spawn... ", min, CommonColors.BLUE)
                max > 0 -> drawStringWithFormat("Corleone is spawning... ", max, CommonColors.YELLOW)
                lastDeath + MAX_TIME > time -> drawStringWithFormat(
                    "Corleone is spawning... ",
                    time - nextMaxSpawn,
                    CommonColors.RED
                )

                else -> reset()
            }
        }

        /**
         * Draws a formatted string with a specific label, time, and color.
         *
         * @param label the label to be displayed
         * @param time the time in seconds
         * @param color the color of the text
         */
        private fun drawStringWithFormat(label: String, time: Long, color: CustomColor) =
            fr.drawString(
                "$label (${time / 60}:${"%02d".format(time % 60)})",
                0f,
                0f,
                color,
                SmartFontRenderer.TextAlignment.LEFT_RIGHT,
                SmartFontRenderer.TextShadow.NONE
            )

        /**
         * Renders the "Corleone is spawning..." message on the screen.
         *
         * This method overrides the parent class's `demoRender` method and uses the `fr` instance
         * of `SmartFontRenderer` to draw the message. The message will be rendered at the coordinate
         * (0, 0) with the specified color, alignment, and text shadow settings.
         *
         * @see SmartFontRenderer
         * @see CommonColors
         * @see SmartFontRenderer.TextAlignment
         * @see SmartFontRenderer.TextShadow
         */
        override fun demoRender() {
            fr.drawString(
                "Corleone is spawning... ",
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