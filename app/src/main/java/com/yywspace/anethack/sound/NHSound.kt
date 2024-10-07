package com.yywspace.anethack.sound

import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.util.Log
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.Utils
import java.io.File


class NHSound(val nh:NetHack) {
    private lateinit var soundPool:SoundPool
    private val soundCache = mutableMapOf<String, Sound>()

    companion object {
        private const val TAG = "NetHackSound"
    }

    private fun playInnerSound(soundName: String, volume: Int) {
        val soundPath = "sound/$soundName.wav"
        if (!Utils.isAssetsFileExists(nh.context, soundPath)) {
            Log.d(TAG, "playInnerSound $soundPath not exists.")
            return
        }
        nh.context.assets.openFd(soundPath).use {
            if (!soundCache.containsKey(soundName)) {
                // 首次加载在onLoadComplete里处理播放，此处把必要的参数保存下来
                soundCache[soundName] = Sound(soundName, soundPool.load(it, 1), volume)
                return
            } else {
                val sound = soundCache[soundName]?:return
                soundPool.play(sound.soundId,volume / 100f,volume / 100f,0,0,1f)
            }
        }
    }

    private fun playUserSound(soundPath: String, volume: Int) {
        val soundFile = File(soundPath)
        if (!soundFile.exists()) {
            Log.d(TAG, "playUserSound $soundPath not exists.")
            return
        }
        if (!soundCache.containsKey(soundPath)) {
            // 首次加载在onLoadComplete里处理播放，此处把必要的参数保存下来
            soundCache[soundPath] = Sound(soundPath,soundPool.load(soundPath, 1), volume)
            return
        } else {
            val sound = soundCache[soundPath]?:return
            soundPool.play(sound.soundId,volume / 100f,volume / 100f,0,0,1f)
        }
    }
    fun initNHSound() {
        Log.d(TAG, "initNHSound")
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(attr)
            .setMaxStreams(5)
            .build()

        soundPool.setOnLoadCompleteListener { soundPool, sampleId, status ->
            // 首次加载时必须在加载完成后播放，否则无声音，此处反向查找soundCache获取首次播放音量
            if(status == 0) {
                soundCache.entries.forEach {
                    val sound = it.value
                    if (sound.soundId == sampleId) {
                        soundPool.play(
                            sound.soundId,sound.firstVolume/ 100f,sound.firstVolume/ 100f,
                            0,0,1f
                        )
                    }
                }
            }
        }
    }

    fun exitNHSound(reason: String) {
        Log.d(TAG, "exitNHSound(reason=$reason)")
        soundPool.release()
    }

    fun soundAchievement(ach1: Int, ach2: AchievementsS2, repeat: Int) {
        Log.d(TAG, "soundAchievement(ach1=$ach1, ach2=$ach2, repeat=$repeat)")
        val ach2SoundMap = mapOf(
            AchievementsS2.SA2_SPLASHSCREEN to "sa2_splashscreen",
            AchievementsS2.SA2_NEWGAME_NOSPLASH to "sa2_newgame_nosplash",
            AchievementsS2.SA2_RESTOREGAME to "sa2_restoregame",
            AchievementsS2.SA2_XPLEVELUP to "sa2_xplevelup",
            AchievementsS2.SA2_XPLEVELDOWN to "sa2_xpleveldown"
        )
        if (ach1 == 0 && ach2 == AchievementsS2.SA2_ZERO_INVALID)
            return
        if (ach1 == 0)
            playInnerSound(ach2SoundMap[ach2]?:"", 100)
    }

    fun soundEffect(soundName:String, desc: String, seid: Int, volume: Int) {
        Log.d(TAG, "soundEffect(soundName=$soundName,desc=$desc, seid=$seid, volume=$volume)")
        playInnerSound(soundName, volume)
    }

    fun heroPlayNotes(instrument: Instruments, notes: String, volume: Int) {
        Log.d(TAG, "heroPlayNotes(instrument=$instrument, notes=$notes, volume=$volume)")
        val insSoundMap = mapOf(
            Instruments.INS_FLUTE to "sound_Wooden_Flute",
            Instruments.INS_PAN_FLUTE to "sound_Magic_Flute",
            Instruments.INS_ENGLISH_HORN to "sound_Tooled_Horn",
            Instruments.INS_FRENCH_HORN to "sound_Frost_Horn",
            Instruments.INS_BARITONE_SAX to "sound_Fire_Horn",
            Instruments.INS_TRUMPET to "sound_Bugle",
            Instruments.INS_ORCHESTRAL_HARP to "sound_Wooden_Harp",
            Instruments.INS_CELLO to "sound_Magic_Harp",
            Instruments.INS_TINKLE_BELL to "sound_Bell",
            Instruments.INS_TAIKO_DRUM to "sound_Drum_Of_Earthquake",
            Instruments.INS_MELODIC_TOM to "sound_Leather_Drum"
        )
        val insNoneNote = listOf(
            Instruments.INS_TINKLE_BELL, Instruments.INS_BARITONE_SAX, Instruments.INS_FRENCH_HORN,
            Instruments.INS_TAIKO_DRUM, Instruments.INS_MELODIC_TOM
        )

        val soundBaseName = insSoundMap[instrument]?:""
        if (instrument !in insNoneNote) {
            Thread {
                notes.toCharArray().forEach {
                    val soundName = "${soundBaseName}_$it"
                    val duration = getSoundDuration(soundName)
                    if (duration > 0) {
                        playInnerSound(soundName, volume)
                        Thread.sleep(duration)
                    }
                }
            }.start()
        } else {
            playInnerSound(soundBaseName, volume)
        }
    }

    fun playUserSound(filename: String, volume: Int, idx: Int) {
        Log.d(TAG, "playUserSound(filename=$filename, volume=$volume, idx=$idx)")
        playUserSound(filename, volume)
    }

    fun soundAmbience(ambience: Ambiences, ambienceAction: AmbienceActions, heroProximity: Int) {
        Log.d(TAG, "soundAmbience(ambience=$ambience, ambienceAction=$ambienceAction, heroProximity=$heroProximity)")
    }

    fun soundVerbal(text: String, gender: Int, tone: Int, vol: Int, moreInfo: Int) {
        Log.d(TAG, "soundVerbal(text=$text, gender=$gender, tone=$tone, vol=$vol, moreInfo=$moreInfo)")
    }

    private fun getSoundDuration(soundName: String): Long {
        val soundPath = "sound/$soundName.wav"
        if (!Utils.isAssetsFileExists(nh.context, soundPath)) {
            Log.d(TAG, "getSoundDuration $soundPath not exists.")
            return 0
        }
        nh.context.assets.openFd(soundPath).use {
            val duration = MediaMetadataRetriever().run {
                setDataSource(it.fileDescriptor, it.startOffset, it.length)
                val durationString = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                release()
                durationString?.toLong()
            }
            return duration?:0
        }
    }

    private data class Sound(val soundName: String, val soundId:Int, val firstVolume: Int)

    enum class AchievementsS2 {
        SA2_ZERO_INVALID,
        SA2_SPLASHSCREEN,
        SA2_NEWGAME_NOSPLASH,
        SA2_RESTOREGAME,
        SA2_XPLEVELUP,
        SA2_XPLEVELDOWN,
        NUMBER_OF_SA2_ENTRIES;

        companion object {
            fun fromInt(value: Int): AchievementsS2 {
                AchievementsS2.values().forEach {
                    if (it.ordinal == value)
                        return it
                }
                return SA2_ZERO_INVALID
            }
        }
    }

    /* subset for NetHack */
    enum class Instruments(val value:Int) {
        INS_CELLO(43), INS_ORCHESTRAL_HARP( 47), INS_CHOIR_AAHS(53),
        INS_TRUMPET(57), INS_TROMBONE(58), INS_FRENCH_HORN(61),
        INS_BARITONE_SAX(68), INS_ENGLISH_HORN(70), INS_PICCOLO(73),
        INS_FLUTE(74), INS_PAN_FLUTE(76), INS_BLOWN_BOTTLE(77),
        INS_WHISTLE(79), INS_TINKLE_BELL(113), INS_WOODBLOCK(116),
        INS_TAIKO_DRUM(117), INS_MELODIC_TOM(118), INS_SEASHORE(123),
        INS_NO_INSTRUMENT(-1);
        companion object {
            fun fromInt(value: Int):Instruments {
                Instruments.values().forEach {
                    if (it.value == value)
                        return it
                }
                return INS_NO_INSTRUMENT
            }
        }
    }
    enum class Ambiences {
        AMB_NOAMBIENCE;

        companion object {
            fun fromInt(value: Int):Ambiences {
                Ambiences.values().forEach {
                    if (it.ordinal == value)
                        return it
                }
                return AMB_NOAMBIENCE
            }
        }
    }

    enum class AmbienceActions {
        AMBIENCE_NOTHING, AMBIENCE_BEGIN, AMBIENCE_END, AMBIENCE_UPDATE;
        companion object {
            fun fromInt(value: Int):AmbienceActions {
                AmbienceActions.values().forEach {
                    if (it.ordinal == value)
                        return it
                }
                return AMBIENCE_NOTHING
            }
        }
    }
}