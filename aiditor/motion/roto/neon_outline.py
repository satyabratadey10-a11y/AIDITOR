"""
Saber Neon Edge & Subject Contour Outline FX
=============================================
Extracts morphological contours of the moving subject and creates electric neon outlines.
"""


class NeonOutlineFX:
    """Generates glowing cyberpunk contour outlines around the tracked subject."""

    @staticmethod
    def build_neon_saber_filtercomplex(
        target_w: int = 480,
        target_h: int = 854,
        neon_color: str = "cyan"  # "cyan", "magenta", "gold"
    ) -> str:
        """
        Extracts subject edges and blends an electric neon saber trace on top of original footage.
        """
        if neon_color == "cyan":
            color_matrix = "colorchannelmixer=rr=0:gg=1.5:bb=2.0"
        elif neon_color == "magenta":
            color_matrix = "colorchannelmixer=rr=2.0:gg=0:bb=2.0"
        else:
            color_matrix = "colorchannelmixer=rr=2.0:gg=1.5:bb=0.2"

        filter_complex = (
            f"[0:v]split=2[main][edge_src];"
            f"[edge_src]scale={target_w}:{target_h},"
            f"edgedetect=low=0.12:high=0.35:mode=colormix,"
            f"{color_matrix},"
            f"boxblur=2:1[neon_edges];"
            f"[main]scale={target_w}:{target_h}[main_scaled];"
            f"[main_scaled][neon_edges]blend=all_mode=addition:all_opacity=0.85"
        )
        return filter_complex
