ReaperNRT {
  classvar <args, <paths, <server, <outFileName, <inputFile;
  classvar <sc_file, <lua_file;

  classvar <>synthArgs;

  /* ------------------ */
  /* Overwriteable methods*/
  /* ------------------ */

    // Overwrite with your own synth func. Make sure it returns a function
  *synthFunc{|numChannels|
    "ReaperNRT should not be used directly. Inherit this class and implement synthFunc in the child class".warn;
    ^{
      var in = SoundIn.ar((0..numChannels-1)); Out.ar(0, LPF.ar(in))
    }
  }

  // Overwrite with your own options
  *serverOptions{|numChannels|
    "ReaperNRT should not be used directly. Inherit this class and implement serverOptions in the child class".warn;
    ^ServerOptions.new
      .numOutputBusChannels_(numChannels)
      .maxSynthDefs_(100000)
      .numInputBusChannels_(numChannels)
  }

  /* ------------------ */
  /* Interface methods  */
  /* ------------------ */

  // Run this to start the script
  *run {|...args|
    synthArgs = args;
    ^this.prInit();
  }

  *generateLuaScript{
    var class = ReaperNRTExampleClass;
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
    // Command line arguments
    args = thisProcess.argv;

    // Paths of sound files to be processed
    paths = args.collect{|argument|
      PathName.new(argument.asString)
    };

    fork{
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
    }

  }

  *prProcess{|pathName|

    var score, synthfunc, opts;
    var cond = Condition.new;
    inputFile = SoundFile.openRead(pathName.fullPath);
	inputFile.close;  // doesn't need to stay open; we just need the stats

    opts = this.serverOptions(inputFile.numChannels);

    // Check opts
    if(opts.isKindOf(ServerOptions).not, { "%: server options not valid.".format(this.name).error; 1.exit});
    server = Server(\nrt, options: opts);

    // @TODO make unique
    outFileName = "%%_nrtprocessed.%".format(
      pathName.pathOnly, pathName.fileNameWithoutExtension, pathName.extension
    ).standardizePath;

    synthfunc = this.synthFunc(inputFile.numChannels);

    // Check syntfunc
    if(synthfunc.isKindOf(Function).not, { "%: synthFunc not valid.".format(this.name).error; 1.exit});

    score = Score([
      [0.0, ['/d_recv',
        SynthDef(\soundprocessor, synthfunc).asBytes
      ]],
      [0.0, Synth.basicNew(\soundprocessor, server).newMsg(args: synthArgs)]
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
