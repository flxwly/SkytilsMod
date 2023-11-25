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

package gg.skytils.skytilsmod.commands.impl

import gg.essential.universal.UChat
import gg.essential.universal.wrappers.message.UMessage
import gg.essential.universal.wrappers.message.UTextComponent
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.failPrefix
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.Skytils.Companion.prefix
import gg.skytils.skytilsmod.Skytils.Companion.successPrefix
import gg.skytils.skytilsmod.commands.BaseCommand
import gg.skytils.skytilsmod.features.impl.mining.MiningFeatures
import gg.skytils.skytilsmod.utils.append
import gg.skytils.skytilsmod.utils.setHoverText
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.event.ClickEvent
import net.minecraft.util.BlockPos
import net.minecraft.util.IChatComponent

object HollowWaypointCommand : BaseCommand("skytilshollowwaypoint", listOf("sthw")) {

    private val diamondVeins: HashMap<String, BlockPos> = hashMapOf(
        "DV-1" to BlockPos(19, 29, 22),
        "DV-2" to BlockPos(34, 48, -35),
        "DV-3" to BlockPos(-3, 67, 22),
        "DV-4" to BlockPos(-31, 51, 40),
        "DV-5" to BlockPos(-17, 41, 42),
        "DV-6" to BlockPos(-19, -38, -17),
        "DV-7" to BlockPos(-13, -38, -24),
        "DV-8" to BlockPos(-14, -35, -40),
        "DV-9" to BlockPos(-10, -36, -48),
        "DV-10" to BlockPos(-22, -38, -38),
        "DV-11" to BlockPos(-28, -37, -43),
        "DV-12" to BlockPos(-31, -37, -38),
        "DV-13" to BlockPos(-41, -38, -43),
        "DV-14" to BlockPos(-47, -35, -37),
        "DV-15" to BlockPos(-45, -35, -29),
        "DV-16" to BlockPos(-42, -35, -19),
        "DV-17" to BlockPos(-28, -35, -9),
        "DV-18" to BlockPos(-25, -22, -3),
        "DV-19" to BlockPos(-34, -22, -3),
        "DV-20" to BlockPos(-34, -22, 3),
        "DV-21" to BlockPos(-25, -22, 3),
        "DV-22" to BlockPos(-16, -38, 0),
        "DV-23" to BlockPos(-27, -36, 19),
        "DV-24" to BlockPos(-37, -37, 22),
        "DV-25" to BlockPos(-44, -35, 28),
        "DV-26" to BlockPos(-43, -34, 32),
        "DV-27" to BlockPos(-29, -37, 35),
        "DV-28" to BlockPos(22, -34, 47),
        "DV-29" to BlockPos(32, -35, 36),
        "DV-30" to BlockPos(29, -35, 28),
        "DV-31" to BlockPos(40, -35, 22),
        "DV-32" to BlockPos(22, -38, 17),
        "DV-33" to BlockPos(32, -34, -15),
        "DV-34" to BlockPos(38, -28, -15),
        "DV-35" to BlockPos(42, -35, -13),
        "DV-36" to BlockPos(37, -38, -31),
        "DV-37" to BlockPos(46, -34, -38),
        "DV-38" to BlockPos(31, -35, -45),
        "DV-39" to BlockPos(21, -32, -45),
        "DV-40" to BlockPos(20, -38, -41),
        "DV-41" to BlockPos(17, -38, -29),
        "DV-42" to BlockPos(10, -38, -26)
    )


    private val syntaxRegex =
        Regex("^(?:(?:(?<x>-?[\\d.]+) (?<y>-?[\\d.]+) (?<z>-?[\\d.]+) (?<name>.+))|(?<nameonly>.+))\$")

    override fun getCommandUsage(player: EntityPlayerSP): String = "/sthw x y z location"

    override fun processCommand(player: EntityPlayerSP, args: Array<String>) {
        if (args.isEmpty()) {
            val message = UMessage("$prefix §eWaypoints:\n")
            for (loc in MiningFeatures.CrystalHollowsMap.Locations.values()) {
                if (!loc.loc.exists()) continue
                message.append("${loc.displayName} ")
                message.append(copyMessage("${loc.cleanName}: ${loc.loc}"))
                message.append(removeMessage(loc.id))
            }
            for ((key, value) in MiningFeatures.waypoints) {
                message.append("§e$key ")
                message.append(copyMessage("$key: ${value.x} ${value.y} ${value.z}"))
                message.append(removeMessage(key))
            }
            message.append("§eFor more info do /sthw help")
            message.chat()
        } else {
            when (args[0]) {
                "set", "add" -> {
                    val remainderString = args.drop(1).joinToString(" ")
                    val match = syntaxRegex.find(remainderString)
                        ?: return UChat.chat("$failPrefix /sthw set <x y z> <name>")
                    val loc: String
                    val x: Double
                    val y: Double
                    val z: Double
                    if (match.groups["nameonly"] != null) {
                        loc = match.groups["nameonly"]!!.value
                        x = mc.thePlayer.posX
                        y = mc.thePlayer.posY
                        z = mc.thePlayer.posZ
                    } else {
                        loc = match.groups["name"]!!.value
                        x = match.groups["x"]!!.value.toDouble()
                        y = match.groups["y"]!!.value.toDouble()
                        z = match.groups["z"]!!.value.toDouble()
                    }
                    val internalLoc = MiningFeatures.CrystalHollowsMap.Locations.values().find { it.id == loc }?.loc
                    if (internalLoc != null) {
                        internalLoc.locX = (x - 200).coerceIn(0.0, 624.0)
                        internalLoc.locY = y
                        internalLoc.locZ = (z - 200).coerceIn(0.0, 624.0)
                    } else {
                        MiningFeatures.waypoints[loc] = BlockPos(x, y, z)
                    }
                    UChat.chat("$successPrefix §aSuccessfully created waypoint $loc")
                }

                "remove", "delete" -> {
                    if (args.size >= 2) {
                        val name = args.drop(1).joinToString(" ")
                        if (MiningFeatures.CrystalHollowsMap.Locations.values()
                                .find { it.id == name }?.loc?.reset() != null
                        )
                            UChat.chat("$successPrefix §aSuccessfully removed waypoint ${name}!")
                        else if (MiningFeatures.waypoints.remove(name) != null)
                            UChat.chat("$successPrefix §aSuccessfully removed waypoint $name")
                        else
                            UChat.chat("$failPrefix §cWaypoint $name does not exist")
                    } else
                        UChat.chat("$prefix §b/sthw remove <name>")
                }

                "clear" -> {
                    MiningFeatures.CrystalHollowsMap.Locations.values().forEach { it.loc.reset() }
                    MiningFeatures.waypoints.clear()
                    UChat.chat("$successPrefix §aSuccessfully cleared all waypoints.")
                }

                "divan_diamonds" -> {
                    for ((key, value) in diamondVeins.entries) {
                        MiningFeatures.waypoints[key] = value.add(mc.thePlayer.position)
                    }
                }

                else -> {
                    UChat.chat(
                        "$prefix §e/sthw ➔ Shows all waypoints\n" +
                                "§e/sthw set name ➔ Sets waypoint at current location\n" +
                                "§e/sthw set x y z name ➔ Sets waypoint at specified location\n" +
                                "§e/sthw remove name ➔ Remove the specified waypoint\n" +
                                "§e/sthw clear ➔ Removes all waypoints"
                    )
                }
            }
        }
    }

    private fun copyMessage(text: String): IChatComponent {
        return UTextComponent("§9[Copy] ").apply {
            setHoverText("§9Copy the coordinates in chat box.")
            clickAction = ClickEvent.Action.SUGGEST_COMMAND
            clickValue = text
        }
    }

    private fun removeMessage(id: String): IChatComponent {
        return UTextComponent("§c[Remove]\n").apply {
            setHoverText("§cRemove the waypoint.")
            clickAction = ClickEvent.Action.RUN_COMMAND
            clickValue = "/sthw remove $id"
        }
    }
}
