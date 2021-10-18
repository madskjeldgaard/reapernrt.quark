-- AUTO GENERATED SCRIPT FOR RUNNING NRT SUPERCOLLIDER CLASS ReaperNRTExampleClass
      -- GENERATED: Mon Oct 18 10:26:04 2021
      -- Get path of this script
      local file_info = debug.getinfo(1,'S');
      local script_path = file_info.source:match[[^@?(.*[/])[^/]-$]]

      -- Add library folder to lua package path to allow requiring libraries
      package.path = script_path .. "/lib/?.lua;" .. package.path
      local nrtrun = require'nrtrun'

      -- Run script with file in same folder as this script. Replace with full path if otherwise
      nrtrun.run( script_path .. "nrt-reapernrtexampleclass.scd" )
