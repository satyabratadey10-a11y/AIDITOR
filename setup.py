from setuptools import setup, find_packages

setup(
    name="aiditor",
    version="3.0.0",
    description="Autonomous AI Video Editing, Motion Tracking, Optical Flow & Audio-Visual VFX Studio",
    long_description=open("README.md", "r", encoding="utf-8").read(),
    long_description_content_type="text/markdown",
    author="Google DeepMind / Antigravity Team",
    packages=find_packages(),
    python_requires=">=3.8",
    install_requires=[],
    entry_points={
        "console_scripts": [
            "aiditor=aiditor.cli:main",
        ],
    },
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: Apache Software License",
        "Operating System :: POSIX :: Linux",
        "Topic :: Multimedia :: Video",
        "Topic :: Multimedia :: Graphics",
    ],
)
