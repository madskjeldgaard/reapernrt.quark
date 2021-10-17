local file_info = debug.getinfo(1,'S');
local script_path = file_info.source:match[[^@?(.*[\/])[^\/]-$]]
-- local utils = loadfile(script_path .. "utils.lua")()
local scfile = script_path .. "reaper-nrt.scd"

-- Debug print
local DEBUG = function(string)
    -- Handy function for quickly debugging strings
    reaper.ShowConsoleMsg(string)
    reaper.ShowConsoleMsg("\n")
end

-- Run command in shell
local cmdline = function(invocation)
    -- Calls the <command> at the system's shell
    -- The implementation slightly differs for each operating system
    -- 06/08/2020 23:26:07 Seems ExecProcess works equally well everywhere
    -- local opsys = reaper.GetOS()
    -- if opsys == "Win64" then retval = reaper.ExecProcess(command, 0) end
    -- if opsys == "OSX64" or opsys == "Other" then  retval = reaper.ExecProcess(command, 0) end
    local retval = reaper.ExecProcess(invocation, 0)

    if not retval then
        DEBUG("There was an error executing the command: "..invocation)
        DEBUG("See the return value and error below:\n")
        DEBUG(tostring(retval))
    end

	return retval
end

-- Get paths of selected items
local num_items_selected = reaper.CountSelectedMediaItems( 0 )
local unique_media_source_paths = {}

-- Check for table value
local function table_has_value (tab, val)
    for _, value in ipairs(tab) do
        if value == val then
            return true
        end
    end

    return false
end

-- File name without extension
local function file_no_extension(file_path)
	return file_path:match("(.+)%..+")
end

-- Get the extension of a string determined by a dot . at the end of the string.
local function file_extension(file_path)
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

-- Process all selected media items
local processed_files = {}
for item_index=1,num_items_selected do

	local item = reaper.GetSelectedMediaItem(0, item_index-1)
	local take = reaper.GetActiveTake(item)
	local src = reaper.GetMediaItemTake_Source(take)
	-- local item = reaper.GetSelectedMediaItem(0, item_index-1)
	local full_path = reaper.GetMediaSourceFileName(src, "")
    -- local src_parent = reaper.GetMediaSourceParent(src)

	-- Add path to paths table
	if not table_has_value(unique_media_source_paths, full_path) then
		table.insert(unique_media_source_paths, full_path)
	end

	-- Add take
	local processed_file_path = file_no_extension(full_path) .. "_nrtprocessed" .. file_extension(full_path)
	processed_files[#processed_files+1] =  processed_file_path
	-- local processed_file_path = "/home/mads/sounds/SamuraiCop.wav"
	reaper.InsertMedia(processed_file_path, 3)

end

local paths_as_string = ""
for _, path in pairs(unique_media_source_paths) do
	path = "\"" .. path .. "\""
	paths_as_string = paths_as_string .. " " .. path
end

local sc_command ="sclang " .. scfile .. " " .. paths_as_string

-- Run sclang command
local command_return = cmdline(sc_command)
-- local function WaitLoop()
-- 	for _,v in pairs(processed_files) do
-- 		reaper.file_exists( processed_file_path )
-- 	end
-- 	if reaper.file_exists( processed_file_path ) then
-- 		DEBUG("DONE...")
-- 		reaper.UpdateArrange()
-- 		reaper.UpdateTimeline()
-- 		return
-- 	else
-- 		DEBUG("Waiting...")
-- 		reaper.defer(WaitLoop)
-- 	end

-- end

-- WaitLoop()

DEBUG("Ran: " .. sc_command .. ".\n------Script output: " .. command_return)

for k,v in pairs(processed_files) do
	if reaper.file_exists(v) then
		DEBUG(v .. " exists")
	end
end

for item_index=1,num_items_selected do
	local item = reaper.GetSelectedMediaItem(0, item_index-1)
	reaper.UpdateItemInProject( item )
end

reaper.UpdateArrange()
reaper.UpdateTimeline()
