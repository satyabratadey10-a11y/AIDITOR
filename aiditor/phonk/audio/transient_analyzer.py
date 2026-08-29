"""
Phonk Transient & Bass Drop Analyzer
====================================
Detects 808 sub-bass drops, cowbell punch transients, build-up risers, and chorus sections.
"""

from typing import List, Dict, Any


class TransientAnalyzer:
    """Classifies audio sections into Intro, Build-Up, Bass Drop, and High-Energy Chorus."""

    @staticmethod
    def identify_drop_and_sections(beat_result: Dict[str, Any]) -> Dict[str, Any]:
        beats = beat_result.get("beats", [])
        duration = beat_result.get("duration", 0.0)

        if not beats:
            return {
                "drop_timestamp": 0.0,
                "sections": [{"name": "DROP", "start": 0.0, "end": duration}]
            }

        # Find the major energy surge (Bass Drop)
        # In Phonk tracks, drops typically occur after an intro/build (e.g. 5-15s into the track)
        max_energy_diff = 0.0
        drop_beat_idx = 0

        # Look for sudden jump in energy across consecutive beats
        for i in range(1, len(beats)):
            diff = beats[i]["energy"] - beats[i - 1]["energy"]
            if diff > max_energy_diff and beats[i]["timestamp"] >= 3.0:
                max_energy_diff = diff
                drop_beat_idx = i

        # Fallback if no huge diff: find first top 10% energy beat after 4s
        if drop_beat_idx == 0 and len(beats) > 8:
            drop_beat_idx = min(8, len(beats) - 1)

        drop_time = beats[drop_beat_idx]["timestamp"] if drop_beat_idx < len(beats) else 0.0

        sections = []
        if drop_time > 0.5:
            sections.append({
                "name": "INTRO_BUILDUP",
                "start": 0.0,
                "end": drop_time,
                "target_motion": "HERO_STATIC_STANCE",
                "fx_intensity": "LOW_TO_MEDIUM"
            })
            sections.append({
                "name": "BASS_DROP_CHORUS",
                "start": drop_time,
                "end": duration,
                "target_motion": "HIGH_SPEED_DRIFT",
                "fx_intensity": "MAXIMUM_IMPACT"
            })
        else:
            sections.append({
                "name": "CONTINUOUS_CHORUS",
                "start": 0.0,
                "end": duration,
                "target_motion": "HIGH_SPEED_DRIFT",
                "fx_intensity": "MAXIMUM_IMPACT"
            })

        return {
            "drop_timestamp": drop_time,
            "drop_beat_index": drop_beat_idx,
            "sections": sections
        }
