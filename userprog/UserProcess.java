package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;

public class UserProcess {
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
        for (int i=0; i<numPhysPages; i++)
            pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
    }

    public static UserProcess newUserProcess() {
        return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;
        new UThread(this).setName(name).fork();
        return true;
    }

    public void saveState() {
    }

    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);
        byte[] bytes = new byte[maxLength+1];
        int bytesRead = readVirtualMemory(vaddr, bytes);
        for (int length=0; length<bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }
        return null;
    }

    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
        byte[] memory = Machine.processor().getMemory();
        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0 || vaddr >= memory.length)
            return 0;
        int amount = Math.min(length, memory.length-vaddr);
        System.arraycopy(memory, vaddr, data, offset, amount);
        return amount;
    }

    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
        byte[] memory = Machine.processor().getMemory();
        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0 || vaddr >= memory.length)
            return 0;
        int amount = Math.min(length, memory.length-vaddr);
        System.arraycopy(data, offset, memory, vaddr, amount);
        return amount;
    }

    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }
        try {
            coff = new Coff(executable);
        }
        catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }
        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s=0; s<coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i=0; i<args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();	

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages*pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages-1)*pageSize;
        int stringOffset = entryOffset + args.length*4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i=0; i<argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
            stringOffset += 1;
        }
        return true;
    }

    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections
        for (int s=0; s<coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

            for (int i=0; i<section.getLength(); i++) {
                int vpn = section.getFirstVPN()+i;
                // for now, just assume virtual addresses=physical addresses
                section.loadPage(i, vpn);
            }
        }
        return true;
    }

    protected void unloadSections() {
    }    

    public void initRegisters() {
        Processor processor = Machine.processor();
        // by default, everything's 0
        for (int i=0; i<processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    private int handleHalt() {
        Machine.halt();
        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    private int handleCreate(int nameAddr) {
        /**
         * Attempt to open the named disk file, creating it if it does not exist,
         * and return a file descriptor that can be used to access the file.
         *
         * Note that creat() can only be used to create files on disk; creat() will
         * never return a file descriptor referring to a stream.
         *
         * Returns the new file descriptor, or -1 if an error occurred.
         */
        // int creat(char *name);
    }

    private int handleOpen(int nameAddr) {
        /**
         * Attempt to open the named file and return a file descriptor.
         *
         * Note that open() can only be used to open files on disk; open() will never
         * return a file descriptor referring to a stream.
         *
         * Returns the new file descriptor, or -1 if an error occurred.
         */
        // int open(char *name);
    }

    private int handleRead(int fileDescriptor, int bufferAddr, int count) {
        /**
         * Attempt to read up to count bytes into buffer from the file or stream
         * referred to by fileDescriptor.
         *
         * On success, the number of bytes read is returned. If the file descriptor
         * refers to a file on disk, the file position is advanced by this number.
         *
         * It is not necessarily an error if this number is smaller than the number of
         * bytes requested. If the file descriptor refers to a file on disk, this
         * indicates that the end of the file has been reached. If the file descriptor
         * refers to a stream, this indicates that the fewer bytes are actually
         * available right now than were requested, but more bytes may become available
         * in the future. Note that read() never waits for a stream to have more data;
         * it always returns as much as possible immediately.
         *
         * On error, -1 is returned, and the new file position is undefined. This can
         * happen if fileDescriptor is invalid, if part of the buffer is read-only or
         * invalid, or if a network stream has been terminated by the remote host and
         * no more data is available.
         */
        // int read(int fileDescriptor, void *buffer, int count);
    }

    private int handleWrite(int fileDescriptor, int bufferAddr, int count) {
        /**
         * Attempt to write up to count bytes from buffer to the file or stream
         * referred to by fileDescriptor. write() can return before the bytes are
         * actually flushed to the file or stream. A write to a stream can block,
         * however, if kernel queues are temporarily full.
         *
         * On success, the number of bytes written is returned (zero indicates nothing
         * was written), and the file position is advanced by this number. It IS an
         * error if this number is smaller than the number of bytes requested. For
         * disk files, this indicates that the disk is full. For streams, this
         * indicates the stream was terminated by the remote host before all the data
         * was transferred.
         *
         * On error, -1 is returned, and the new file position is undefined. This can
         * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
         * if a network stream has already been terminated by the remote host.
         */
        // int write(int fileDescriptor, void *buffer, int count);
    }

    private int handleClose(int fileDescriptor) {
        /**
         * Close a file descriptor, so that it no longer refers to any file or stream
         * and may be reused.
         *
         * If the file descriptor refers to a file, all data written to it by write()
         * will be flushed to disk before close() returns.
         * If the file descriptor refers to a stream, all data written to it by write()
         * will eventually be flushed (unless the stream is terminated remotely), but
         * not necessarily before close() returns.
         *
         * The resources associated with the file descriptor are released. If the
         * descriptor is the last reference to a disk file which has been removed using
         * unlink, the file is deleted (this detail is handled by the file system
         * implementation).
         *
         * Returns 0 on success, or -1 if an error occurred.
         */
        // int close(int fileDescriptor);
    }

    private int handleUnlink(int nameAddr) {
        /**
         * Delete a file from the file system. If no processes have the file open, the
         * file is deleted immediately and the space it was using is made available for
         * reuse.
         *
         * If any processes still have the file open, the file will remain in existence
         * until the last file descriptor referring to it is closed. However, creat()
         * and open() will not be able to return new file descriptors for the file
         * until it is deleted.
         *
         * Returns 0 on success, or -1 if an error occurred.
         */
        // int unlink(char *name);
    }

    private static final int
        syscallHalt = 0,
        syscallExit = 1,
        syscallExec = 2,
        syscallJoin = 3,
        syscallCreate = 4,
        syscallOpen = 5,
        syscallRead = 6,
        syscallWrite = 7,
        syscallClose = 8,
        syscallUnlink = 9;

    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    public void handleException(int cause) {
        Processor processor = Machine.processor();
        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                                           processor.readRegister(Processor.regA0),
                                           processor.readRegister(Processor.regA1),
                                           processor.readRegister(Processor.regA2),
                                           processor.readRegister(Processor.regA3) );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
            Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
            Lib.assertNotReached("Unexpected exception");
        }
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
}
