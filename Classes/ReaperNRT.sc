ReaperNRT {
  classvar <args, <paths, <server, <outFileName, <inputFile;
  classvar <sc_file, <lua_file;
  classvar cond;
  classvar argDict;

  /* ------------------ */
  /* Overwriteable methods*/
  /* ------------------ */

    // Overwrite with your own synth func. Make sure it returns a function
  *synthFunc{|numChannels|
    "ReaperNRT synthfunc not implemented in child class".error;
    1.exit;
    // ^{|cutoff=1500|
    //   var in = SoundIn.ar((0..numChannels-1)); Out.ar(0, LPF.ar(in, cutoff))
    // }
  }

  // Overwrite with your own options
  *serverOptions{|numChannels|
    "ReaperNRT serverOptions not implemented in child class".error;
    1.exit;
    // ^ServerOptions.new
    //   .numOutputBusChannels_(numChannels)
    //   .maxSynthDefs_(100000)
    //   .numInputBusChannels_(numChannels)
  }

  /* ------------------ */
  /* Interface methods  */
  /* ------------------ */

  // Run this to start the script
  *run {
    ^this.prInit();
  }

  *gui{
    var window;
    var sliders;
    var specs = this.specs();
    var button;

    if(specs.isKindOf(Dictionary).not, {
      "%: No specs".format(this.class.name); 1.exit
    }, {
      window = Window.new();

      window.onClose_({
        "%: User closed window. Aborting NRT process".format(this.class.name).error;
        1.exit;
      });

      button = Button.new(window)
      .states_([["compose"]])
      .action_({

        // @TODO: This is dirty.
        // Remove onclose action
        window.onClose_({});

        window.close;
        cond.unhang;
      });

      sliders = specs.collect{|spec, name|
        var slider;
        var label = StaticText.new(window).string_(name);
        var valueBox = NumberBox.new(window).value_(spec.default);
        var unit = StaticText.new(window).string_(spec.units);
        slider = Slider.new(window)
        .orientation_(\horizontal)
        .value_(spec.default)
        .action_({|obj|
          var mappedVal = spec.map(obj.value);
          argDict[name] = mappedVal;
          valueBox.value = mappedVal;
        });

        HLayout.new(label, slider, valueBox, unit)
      }.asArray;

      window.layout = VLayout(button, VLayout.new(*sliders));

      window.front;

    });
  }

  *specs{
    "ReaperNRT specs not implemented in child class".error;
    1.exit;
    // ^(
    //   cutoff: ControlSpec.new(
    //     minval: 50.0,  maxval: 15500.0,  warp: \exp,  step: 0.0,  default: 500,  units: "hz"
    //   )
    // )
  }

  *generateLuaScript{
    var class = this.class;
    // @TODO Put this in user resources folder instead?
    var luaFolder = Main.packages.asDict['reapernrt.quark'] +/+ "lua";
    var scriptName = class.name.toLower;
    var fileName = "nrt-%".format(scriptName);

    var lua_file_name = luaFolder +/+ fileName ++ ".lua";
    var sc_file_name = luaFolder +/+ fileName ++ ".scd";

    lua_file = File(lua_file_name, "w");
    sc_file = File(sc_file_name, "w");

    // Generate SuperCollider script
    "Creating file % and putting it in %".format(sc_file_name, luaFolder).postln;
    sc_file.write("(\n%.run\n)".format(class));
    sc_file.close;

    // Generate lua script
    "Creating file % and putting it in %".format(lua_file_name, luaFolder).postln;

    lua_file.write("-- AUTO GENERATED SCRIPT FOR RUNNING NRT SUPERCOLLIDER CLASS %
      -- GENERATED: %
      -- Get path of this script
      local file_info = debug.getinfo(1,'S');
      local script_path = file_info.source:match[[^@?(.*[\/])[^\/]-$]]

      -- Add library folder to lua package path to allow requiring libraries
      package.path = script_path .. \"/lib/?.lua;\" .. package.path
      local nrtrun = require'nrtrun'

      -- Run script with file in same folder as this script. Replace with full path if otherwise
      nrtrun.run( script_path .. \"%\" )".format(class, Date.getDate, fileName ++ ".scd"));

      lua_file.close;
    }

  /* ------------------ */
  /* Private methods    */
  /* ------------------ */
  *prInit {
    var routine, clock;
    argDict = ();

    // Command line arguments
    args = thisProcess.argv;

    // Paths of sound files to be processed
    paths = args.collect{|argument|
      PathName.new(argument.asString)
    };

    routine = Routine({
      if(paths.size == 0, {"No argument supplied for path".error; 1.exit}, {
        paths.do{|path, index|
          if(path.isFile, {
            this.prProcess(path)
          }, {
            1.exit
          });
        };

        0.exit

      });
    });

    AppClock.play(routine);
  }

  *prProcess{|pathName|
    try{

      var score, synthfunc, opts;

      cond = Condition.new;

      this.gui();
      cond.hang;

      inputFile = SoundFile.openRead(pathName.fullPath);
      inputFile.close;  // doesn't need to stay open; we just need the stats

      opts = this.serverOptions(inputFile.numChannels);

      // Check opts
      if(opts.isKindOf(ServerOptions).not or: {opts.isNil}, { "%: server options not valid.".format(this.name).error; 1.exit});
      server = Server(\nrt, options: opts);

      // @TODO make unique
      outFileName = "%%_nrtprocessed.%".format(
        pathName.pathOnly, pathName.fileNameWithoutExtension, pathName.extension
      ).standardizePath;

      synthfunc = this.synthFunc(inputFile.numChannels);

      // Check syntfunc
      if(synthfunc.isKindOf(Function).not or: {synthfunc.isNil}, { "%: synthFunc not valid.".format(this.name).error; 1.exit});

      score = Score([
        [0.0, ['/d_recv',
          SynthDef(\soundprocessor, synthfunc).asBytes
        ]],
        [0.0, Synth.basicNew(\soundprocessor, server).newMsg(args: argDict.asArgsArray)]
      ]);

      score.recordNRT(
        outputFilePath: outFileName,
        inputFilePath: inputFile.path,
        sampleRate: inputFile.sampleRate,
        headerFormat: inputFile.headerFormat,
        sampleFormat: inputFile.sampleFormat,
        options: server.options,
        duration: inputFile.duration,
        action: {
          cond.unhang()
        }
      );

      cond.hang();
      "Done processing % using %".format(pathName.fileName, this.name).postln;

      server.remove;

    } { |error|
      1.exit
      // if() {
      // } { error.throw }
    }
  }

}

ReaperNRTExampleClass : ReaperNRT {

  *synthFunc{|numChannels|
    ^{|t60=1, damp=0, size=1, modDepth=0.5, modFreq=0.01|
      var in = SoundIn.ar((0..numChannels-1));
      var sig = JPverb.ar(
        in,
        t60: t60,
        damp: damp,
        size: size,
        earlyDiff: 0.707,
        modDepth: modDepth,
        modFreq: modFreq,
        low: 1.0,
        mid: 1.0,
        high: 1.0,
        lowcut: 500.0,
        highcut: 2000.0
      );

      Out.ar(0, sig)
    }
  }

  *serverOptions{|numChannels|
    ^ServerOptions.new
    .numOutputBusChannels_(numChannels)
    .maxSynthDefs_(100000)
    .memSize_(65536 * 4)
    .numInputBusChannels_(numChannels)
  }

}
