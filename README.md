# ReaperNRT.quark

### Integrating SuperCollider Non-realtime processing in Reaper

A slightly longer description of this package

Inspired by [ReaComa](https://github.com/ReaCoMa/ReaCoMa-2.0)(Thanks for the help, James!).

### Installation

Open up SuperCollider and evaluate the following line of code:
`Quarks.install("https://github.com/madskjeldgaard/reapernrt.quark")`

### sclang executable
Make sure `sclang` is in your path before using this. On most Linux systems this is already setup, but on MacOS you may need to set it up yourself.

To verify that `sclang` is executable, open up a terminal and run `which sclang`. If it does not output anything, you need to fix this before continuing.

## Usage

### Gotchas
- Make sure your SuperCollider class library can compile. If it cannot, Reaper will hang until you kill the sclang process externally.
