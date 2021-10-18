ReaperNRT {
  classvar <args, <paths, <server, <outFileName, <inputFile;
  classvar <sc_file, <lua_file;
  classvar cond;
  classvar argDict;
  classvar <fileTimeStamp;

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
    "ReaperNRT serverOptions not implemented in child class".warn;
    ^ServerOptions.new
      .numOutputBusChannels_(numChannels)
      .memSize_(65536)
      .numInputBusChannels_(numChannels)
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
    var title;

    if(specs.isKindOf(Dictionary).not, {
      "%: No specs".format(this.class.name); 1.exit
    }, {
      window = Window.new().background_(Color.white);

      window.onClose_({
        "%: User closed window. Aborting NRT process".format(this.class.name).error;
        1.exit;
      });

      button = Button.new(window)
      .states_([["compose", Color.black, Color.white]])
      .action_({

        // @TODO: This is dirty.
        // Remove onclose action
        window.onClose_({});

        window.close;
        cond.unhang;
      });

      sliders = specs.collect{|spec, name|
        var slider;
        var label = StaticText.new(window, Rect.new(20, 100, 40, 20)).string_(name).font_(Font.default.bold_(true));
        var valueBox = NumberBox.new(window, Rect.new(20, 100, 40, 20)).value_(spec.default);
        var unit = StaticText.new(window).string_(spec.units);
        slider = Slider.new(window, Rect(20, 100, 100, 20))
        .orientation_(\horizontal)
        .background_(Color.grey(0.98))
        .value_(spec.default)
        .action_({|obj|
          var mappedVal = spec.map(obj.value);
          argDict[name] = mappedVal;
          valueBox.value = mappedVal;
        });

        HLayout.new([label, s: 2], [slider, s: 3], [valueBox, s: 1], [unit, s: 1])
      }.asArray;

      title = StaticText.new(window).string_(this.name).font_(Font.default.bold_(true).size_(16));
      window.layout = VLayout([title, s: 1], [button, s: 1], VLayout.new(*sliders));

      // window.view.decorator = FlowLayout( window.view.bounds, 10@10, 20@5 );
      window.front;

    });
  }

  *libPath{
    ^Main.packages.asDict['reapernrt.quark']
  }

  *getPath{
    var resourcePath = Platform.userAppSupportDir +/+ "reapernrt";

    PathName(resourcePath).isFolder.not.if{
      "Resource path for % does not exist. Creating now: %\n.".format(resourcePath).warn;
      File.mkdir(resourcePath);
    };

    ^resourcePath
  }

  *openOS{
    this.getPath().openOS;
  }

  *installAll{
    ReaperNRT.allSubclasses.do{ |sub|
      sub.generateLuaScript();
    }
  }

  *generateLuaScript{
    var class = this.name;
    // @TODO Put this in user resources folder instead?
    var luaFolder = this.getPath();
    var scriptName = this.name.toLower;
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
      local library_path = \"%\"
      package.path = library_path .. \"/lib/?.lua;\" .. package.path
      local nrtrun = require'nrtrun'

      -- Run script with file in same folder as this script. Replace with full path if otherwise
      nrtrun.run( script_path  .. \"%\" )".format(class, Date.getDate, this.libPath() +/+ "lua", fileName ++ ".scd"));

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

    fileTimeStamp = args[0];

    "Timestamp: %".format(fileTimeStamp).postln;

    args = args[1..];

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
      outFileName = "%%_nrtprocessed_%.%".format(
        pathName.pathOnly, pathName.fileNameWithoutExtension, fileTimeStamp, pathName.extension
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

// This is an example of using the ReaperNRT class:
ReaperNRTJPVerb : ReaperNRT {

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

  *specs{
    ^(
      t60: ControlSpec.new(
        minval: 0.1,  maxval: 60.0,  warp: \exp,  step: 0.0,  default: 1,  units: "seconds"
      ),
      damp: ControlSpec.new(
        minval: 0.0,  maxval: 1.0,  warp: \lin,  step: 0.0,  default: 0.1,  units: ""
      ),
      size: ControlSpec.new(
        minval: 0.5,  maxval: 5,  warp: \exp,  step: 0.0,  default: 1,  units: "size"
      ),
      modDepth: ControlSpec.new(
        minval: 0.0,  maxval: 1.0,  warp: \lin,  step: 0.0,  default: 0.5,  units: ""
      ),
      modFreq: ControlSpec.new(
        minval: 0.00001,  maxval: 10.0,  warp: \exp,  step: 0.0,  default: 0.01,  units: "hz"
      ),
    )
  }

  // This is optional, but a good idea to implement in the child class
  *serverOptions{|numChannels|
    ^ServerOptions.new
    .numOutputBusChannels_(numChannels)
    .maxSynthDefs_(100000)
    .memSize_(65536 * 4)
    .numInputBusChannels_(numChannels)
  }

}
