ReaperNRT {
  classvar <args, <paths, <server, <outFileName, <inputFile;

  classvar <>synthArgs;

  // Run this to start the script
  *run {|...args|
    synthArgs = args;
    ^this.prInit();
  }

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

    var score;
    var cond = Condition.new;
    inputFile = SoundFile.openRead(pathName.fullPath);
	inputFile.close;  // doesn't need to stay open; we just need the stats

    server = Server(\nrt, options: this.serverOptions(inputFile.numChannels));

    // @TODO make unique
    outFileName = "%%_nrtprocessed.%".format(
      pathName.pathOnly, pathName.fileNameWithoutExtension, pathName.extension
    ).standardizePath;

    score = Score([
      [0.0, ['/d_recv',
      SynthDef(\soundprocessor, this.synthFunc(inputFile.numChannels)).asBytes
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
