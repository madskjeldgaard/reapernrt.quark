local M = {}
local file_info = debug.getinfo(1,'S');
local script_path = file_info.source:match[[^@?(.*[\/])[^\/]-$]]
-- local utils = loadfile(script_path .. "utils.lua")()

-- Debug print
M.DEBUG = function(string)
    -- Handy function for quickly debugging strings
    reaper.ShowConsoleMsg(string)
    reaper.ShowConsoleMsg("\n")
end

-- Run command in shell
M.cmdline = function(invocation)
    -- Calls the <command> at the system's shell
    -- The implementation slightly differs for each operating system
    -- 06/08/2020 23:26:07 Seems ExecProcess works equally well everywhere
    -- local opsys = reaper.GetOS()
    -- if opsys == "Win64" then retval = reaper.ExecProcess(command, 0) end
    -- if opsys == "OSX64" or opsys == "Other" then  retval = reaper.ExecProcess(command, 0) end
    local retval = reaper.ExecProcess(invocation, 0)

    if not retval then
        M.DEBUG("There was an error executing the command: "..invocation)
        M.DEBUG("See the return value and error below:\n")
        M.DEBUG(tostring(retval))
    end

	return retval
end

-- Check for table value
function M.table_has_value (tab, val)
    for _, value in ipairs(tab) do
        if value == val then
            return true
        end
    end

    return false
end

-- File name without extension
function M.file_no_extension(file_path)
	return file_path:match("(.+)%..+")
end

-- Get the extension of a string determined by a dot . at the end of the string.
function M.file_extension(file_path)
    local str = file_path
  local temp = ""
  local result = "." -- ! Remove the dot here to ONLY get the extension, eg. jpg without a dot. The dot is added because Download() expects a file type with a dot.

  for i = str:len(), 1, -1 do
    if str:sub(i,i) ~= "." then
      temp = temp..str:sub(i,i)
    else
      break
    end
  end

  -- Reverse order of full file name
  for j = temp:len(), 1, -1 do
    result = result..temp:sub(j,j)
  end

  return result
end

-- Get info from selected media items
M.processed_files = {}
M.unique_media_source_paths = {}
M.num_items_selected = reaper.CountSelectedMediaItems( 0 )
M.items = {}
function M.get_files_info()
	-- Process all selected media items
	for item_index=1,M.num_items_selected do
		local thisItem = {}

		thisItem.item = reaper.GetSelectedMediaItem(0, item_index-1)
		thisItem.take = reaper.GetActiveTake(thisItem.item)
		thisItem.src = reaper.GetMediaItemTake_Source(thisItem.take)
		-- local item = reaper.GetSelectedMediaItem(0, item_index-1)
		thisItem.full_path = reaper.GetMediaSourceFileName(thisItem.src, "")
		-- local src_parent = reaper.GetMediaSourceParent(src)

		-- Add path to paths table
		if not M.table_has_value(M.unique_media_source_paths, thisItem.full_path) then
			table.insert(M.unique_media_source_paths, thisItem.full_path)
		end

		M.items[item_index] = thisItem
	end
end

function M.add_processed_files_as_takes()
	for item_index=1,M.num_items_selected do

		-- Add take
		local processed_file_path = M.file_no_extension(M.items[item_index].full_path) .. "_nrtprocessed" .. M.file_extension(M.items[item_index].full_path)
		M.processed_files[#M.processed_files+1] =  processed_file_path
		-- local processed_file_path = "/home/mads/sounds/SamuraiCop.wav"
		reaper.InsertMedia(processed_file_path, 3)

		reaper.UpdateArrange()
		reaper.UpdateTimeline()
	end
end

function M.run(scfile)

	-- Get info on selected files
	M.get_files_info()

	-- Create string for command line
	M.paths_as_string = ""
	for _, path in pairs(M.unique_media_source_paths) do
		path = "\"" .. path .. "\""
		M.paths_as_string = M.paths_as_string .. " " .. path
	end
	local sc_command ="sclang " .. scfile .. " " .. M.paths_as_string

	-- Run sclang command
	local command_return = M.cmdline(sc_command)

	-- Add as new take and update
	M.add_processed_files_as_takes()

	-- Post debug info
	M.DEBUG("Ran: " .. sc_command .. ".\n------Script output: " .. command_return)
end

M.run( script_path .. "../reaper-nrt.scd" )

return M
